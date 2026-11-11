package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tripbiseo.live.dto.SqlCheck;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * ④ AI(Gemini)가 쓴 SQL 검사. 통과한 SQL만 AskSqlRunner가 ask_reader 계정으로 실행해요.
 * 검사: 글자(#{ ${ ; 주석) → 문법 → SELECT 하나 → 첫 칸 id → 표 → 함수 → 빈칸 → 숫자 상한 → LIMIT
 * 통과하면 빈칸 :route·:me·:next → ST_GeogFromText(#{route}) 처럼, :slack → #{slack}으로 바꿔요 (MyBatis가 값을 안전하게 넣음)
 */
@Component
public class SqlChecker {

    private static final int MAX_LIMIT = 10;          // 결과는 많아야 10곳
    private static final int MAX_DETOUR_MIN = 15;     // 돌아가는 시간 상한(분)
    private static final int MAX_METERS = 1000;       // ST_DWithin 반경 상한(m)
    private static final Set<String> ALLOWED_TABLES = Set.of("ask_place", "ask_review", "ask_menu"); // ask_menu: 메뉴·가격 (11/11)
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "detour_minutes", "st_dwithin", "st_distance", "count", "coalesce", "lower", "round", "least", "greatest");
    private static final Set<String> GEO_PLACEHOLDERS = Set.of("route", "me", "next"); // 위치 빈칸
    private static final String SLACK = "slack";                                        // 숫자 빈칸 (여유 분)
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<![:\\w]):(route|me|next|slack)\\b");

    /** 검사하고, 통과하면 실행할 SQL로 바꿔서 돌려줌 */
    public SqlCheck checkSql(String sql) {
        String text = sql == null ? "" : sql.trim();
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        String badText = findBadText(text);
        if (badText != null) {
            return fail(badText);
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(text);
        } catch (JSQLParserException e) {
            return fail("SQL 문법 오류");
        }
        if (!isSelectOnly(statement)) {
            return fail("SELECT 하나만 쓸 수 있음 (UNION, WITH, INSERT·UPDATE·DELETE 안 됨)");
        }
        PlainSelect select = (PlainSelect) statement;
        if (!isFirstColumnId(select)) {
            return fail("첫 번째 칸은 id여야 함");
        }

        Collector collector = new Collector();
        Set<String> tables = collector.getTables(statement);
        if (collector.problem != null) {
            return fail(collector.problem);
        }
        for (String table : tables) {
            if (!isAllowedTable(table)) {
                return fail("쓸 수 없는 표: " + table + " (ask_place, ask_review만)");
            }
        }
        for (String function : collector.functions) {
            if (!isAllowedFunction(function)) {
                return fail("쓸 수 없는 함수: " + function);
            }
        }

        fixLimit(select);
        SqlCheck check = new SqlCheck();
        check.setOk(true);
        check.setSql(toMyBatis(select.toString()));
        check.setPlaceholders(collector.placeholders);
        check.setReviewWords(collector.reviewWords);
        return check;
    }

    // ---------------------------------------------------------------- 검사 항목

    /** 글자만 봐도 안 되는 것: 빈 SQL, MyBatis 문법(#{ ${), 여러 문장(;), 주석 */
    private String findBadText(String text) {
        if (text.isEmpty()) {
            return "SQL이 비어 있음";
        }
        if (text.contains("#{") || text.contains("${")) {
            return "#{ 나 ${ 는 쓸 수 없음";
        }
        if (text.contains(";")) {
            return "문장은 하나만 (; 안 됨)";
        }
        if (text.contains("--") || text.contains("/*")) {
            return "주석은 쓸 수 없음";
        }
        return null;
    }

    /** SELECT 하나인지 (UNION·WITH·SELECT INTO 안 됨) */
    private boolean isSelectOnly(Statement statement) {
        if (!(statement instanceof PlainSelect select)) {
            return false;
        }
        return select.getWithItemsList() == null && select.getIntoTables() == null;
    }

    /** 첫 번째 칸이 id인지 (p.id도 됨). 장소 id만 받아서 정보는 우리 고정 SQL이 붙여요 */
    private boolean isFirstColumnId(PlainSelect select) {
        Expression first = select.getSelectItems().get(0).getExpression();
        return first instanceof Column column && "id".equalsIgnoreCase(column.getColumnName());
    }

    private boolean isAllowedTable(String table) {
        return ALLOWED_TABLES.contains(table.toLowerCase());
    }

    private boolean isAllowedFunction(String function) {
        return ALLOWED_FUNCTIONS.contains(function.toLowerCase());
    }

    /** LIMIT이 없거나 10보다 크면(또는 숫자가 아니면) 10으로. OFFSET·FETCH는 지움 */
    private void fixLimit(PlainSelect select) {
        Limit limit = select.getLimit();
        boolean isSmall = limit != null && limit.getOffset() == null
                && limit.getRowCount() instanceof LongValue count && count.getValue() <= MAX_LIMIT;
        if (!isSmall) {
            select.setLimit(new Limit().withRowCount(new LongValue(MAX_LIMIT)));
        }
        select.setOffset(null);
        select.setFetch(null);
    }

    /** 빈칸을 MyBatis 파라미터로: :route → ST_GeogFromText(#{route}), :slack → #{slack} */
    private String toMyBatis(String sql) {
        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = SLACK.equals(name) ? "#{slack}" : "ST_GeogFromText(#{" + name + "})";
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private SqlCheck fail(String reason) {
        SqlCheck check = new SqlCheck();
        check.setOk(false);
        check.setReason(reason);
        return check;
    }

    // ---------------------------------------------------------------- SQL 구석구석 훑기

    /**
     * 표 이름을 찾는 JSqlParser 도우미(TablesNamesFinder)가 SQL을 구석구석(하위 쿼리 포함) 훑을 때
     * 함수·빈칸·리뷰 검색어·숫자 상한도 같이 봐요
     */
    private static class Collector extends TablesNamesFinder<Void> {
        private final Set<String> functions = new LinkedHashSet<>();
        private final Set<String> placeholders = new LinkedHashSet<>();
        private final List<String> reviewWords = new ArrayList<>();
        private String problem; // 찾은 문제 (없으면 null)

        @Override
        public <S> Void visit(Function function, S context) {
            String name = function.getName().toLowerCase();
            functions.add(name);
            if ("st_dwithin".equals(name)) {
                checkMeters(function);
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(JdbcNamedParameter parameter, S context) {
            String name = parameter.getName().toLowerCase();
            if (!GEO_PLACEHOLDERS.contains(name) && !SLACK.equals(name)) {
                problem = "쓸 수 없는 빈칸: :" + parameter.getName() + " (:route, :me, :next, :slack만)";
            }
            placeholders.add(name);
            return super.visit(parameter, context);
        }

        @Override
        public <S> Void visit(StringValue value, S context) {
            if (value.getValue().contains(":")) {
                problem = "글자 안에 : 는 쓸 수 없음";
            }
            return super.visit(value, context);
        }

        /** 리뷰 글 검색어 꺼내기: content ILIKE '%조용%' → 조용 */
        @Override
        public <S> Void visit(LikeExpression like, S context) {
            if (like.getLeftExpression() instanceof Column column && "content".equalsIgnoreCase(column.getColumnName())
                    && like.getRightExpression() instanceof StringValue word) {
                String clean = word.getValue().replace("%", "").replace("_", "").trim();
                if (!clean.isEmpty() && !reviewWords.contains(clean)) {
                    reviewWords.add(clean);
                }
            }
            return super.visit(like, context);
        }

        @Override
        public <S> Void visit(MinorThan expression, S context) {
            checkDetour(expression);
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(MinorThanEquals expression, S context) {
            checkDetour(expression);
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(GreaterThan expression, S context) {
            checkDetour(expression);
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(GreaterThanEquals expression, S context) {
            checkDetour(expression);
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(EqualsTo expression, S context) {
            checkDetour(expression);
            return super.visit(expression, context);
        }

        /** detour_minutes(…) 와 비교하는 숫자가 15분을 넘으면 문제 */
        private void checkDetour(BinaryExpression expression) {
            boolean hasDetour = isDetour(expression.getLeftExpression()) || isDetour(expression.getRightExpression());
            Double number = numberOf(isDetour(expression.getLeftExpression())
                    ? expression.getRightExpression() : expression.getLeftExpression());
            if (hasDetour && number != null && number > MAX_DETOUR_MIN) {
                problem = "돌아가는 시간은 " + MAX_DETOUR_MIN + "분 이하로";
            }
        }

        /** ST_DWithin(위치, 위치, 반경) 반경이 1000m를 넘으면 문제 */
        private void checkMeters(Function function) {
            if (function.getParameters() == null || function.getParameters().size() < 3) {
                return;
            }
            Double meters = numberOf((Expression) function.getParameters().get(2));
            if (meters != null && meters > MAX_METERS) {
                problem = "반경은 " + MAX_METERS + "m 이하로";
            }
        }

        private boolean isDetour(Expression expression) {
            return expression instanceof Function function && "detour_minutes".equalsIgnoreCase(function.getName());
        }

        /** 숫자 글자면 그 값, 아니면 null */
        private Double numberOf(Expression expression) {
            if (expression instanceof LongValue value) {
                return (double) value.getValue();
            }
            if (expression instanceof DoubleValue value) {
                return value.getValue();
            }
            return null;
        }
    }
}
