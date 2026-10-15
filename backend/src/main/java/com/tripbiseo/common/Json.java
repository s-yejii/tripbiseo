package com.tripbiseo.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 작은 JSON 도구(외부 라이브러리 없음).
 *  - parse : 문자열 → Map / List / String / Long / Double / Boolean / null
 *  - write : Map·List·배열·문자열·숫자·불리언 → JSON 문자열
 * DB의 *_json 칸과 외부 API(Gemini·TMAP·TourAPI …) 응답을 읽고 쓸 때 씁니다.
 */
public final class Json {

    private Json() {}

    // ---------------------------------------------------------------- 쓰기
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof String s) quote(sb, s);
        else if (v instanceof Boolean b) sb.append(b);
        else if (v instanceof Double d) sb.append(d.isNaN() || d.isInfinite() ? "null" : (d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : d.toString()));
        else if (v instanceof Float f) write(sb, f.doubleValue());
        else if (v instanceof Number n) sb.append(n);
        else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else if (v instanceof double[] arr) {
            List<Object> list = new ArrayList<>();
            for (double d : arr) list.add(d);
            write(sb, list);
        } else if (v instanceof Object[] arr) write(sb, List.of(arr));
        else quote(sb, v.toString());
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- 꺼내기
    /** 중첩 JSON에서 값 꺼내기. 키는 문자열, 배열 위치는 숫자. 중간에 없으면 null. 예) at(res, "candidates", 0, "content") */
    public static Object at(Object root, Object... path) {
        Object cur = root;
        for (Object key : path) {
            if (cur instanceof Map<?, ?> m && key instanceof String k) cur = m.get(k);
            else if (cur instanceof List<?> l && key instanceof Integer idx) cur = idx >= 0 && idx < l.size() ? l.get(idx) : null;
            else return null;
            if (cur == null) return null;
        }
        return cur;
    }

    public static String str(Object root, Object... path) {
        Object v = at(root, path);
        return v == null ? null : v instanceof String s ? s : String.valueOf(v);
    }

    public static Double num(Object root, Object... path) {
        Object v = at(root, path);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object root, Object... path) {
        Object v = at(root, path);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    /** 배열이면 그대로, 객체 하나면 [객체], 없으면 빈 목록 (공공데이터 API는 결과 1건이면 배열이 아니라 객체로 옵니다) */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object root, Object... path) {
        Object v = at(root, path);
        if (v instanceof List) return (List<Object>) v;
        if (v instanceof Map) return new ArrayList<>(List.of(v));
        return new ArrayList<>();
    }

    // ---------------------------------------------------------------- 읽기
    /** 형식이 틀리면 IllegalArgumentException. */
    public static Object parse(String text) {
        Parser p = new Parser(text == null ? "" : text);
        p.skip();
        Object v = p.value();
        p.skip();
        if (p.i != p.s.length()) throw new IllegalArgumentException("JSON 뒤에 남은 글자가 있습니다.");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("JSON 객체가 아닙니다.");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            if (i >= s.length()) throw new IllegalArgumentException("JSON이 비어 있습니다.");
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skip();
            if (peek('}')) return m;
            while (true) {
                skip();
                String key = string();
                skip();
                expect(':');
                skip();
                m.put(key, value());
                skip();
                if (peek('}')) return m;
                expect(',');
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++;
            skip();
            if (peek(']')) return list;
            while (true) {
                skip();
                list.add(value());
                skip();
                if (peek(']')) return list;
                expect(',');
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw new IllegalArgumentException("\\u 형식 오류");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            }
            throw new IllegalArgumentException("문자열이 닫히지 않았습니다.");
        }

        Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw new IllegalArgumentException("JSON 형식 오류 (" + start + "번째 글자)");
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
                return Long.parseLong(t);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(t);
            }
        }

        boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) { i++; return true; }
            return false;
        }

        void expect(char c) {
            if (!peek(c)) throw new IllegalArgumentException("JSON 형식 오류: '" + c + "' 필요 (" + i + "번째 글자)");
        }
    }
}
