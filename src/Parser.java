import java.io.*;
import java.util.*;

public class Parser {
    // 文法表示
    private static final String START_SYMBOL = "program'";
    private Map<String, List<List<String>>> productions = new LinkedHashMap<>();
    private Set<String> nonTerminals = new LinkedHashSet<>();
    private Set<String> terminals = new LinkedHashSet<>();
    private Map<String, Set<String>> first = new HashMap<>();
    private Map<String, Set<String>> follow = new HashMap<>();

    // LR(1) 状态集合
    private List<Set<Item>> states = new ArrayList<>();
    private Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();
    private Map<Integer, Map<String, Action>> actionTable = new HashMap<>();

    public static class Action {
        enum Type { SHIFT, REDUCE, ACCEPT }
        Type type;
        int value;      // shift to state or reduce production index
        public Action(Type t, int v) { type = t; value = v; }
        @Override public String toString() {
            switch (type) {
                case SHIFT:  return "s" + value;
                case REDUCE: return "r" + value;
                case ACCEPT: return "acc";
                default: return "";
            }
        }
    }

    private static class Item {
        String lhs;
        List<String> rhs;
        int dot;
        String lookahead;
        public Item(String L, List<String> R, int d, String la) {
            lhs = L; rhs = R; dot = d; lookahead = la;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Item)) return false;
            Item i = (Item) o;
            return lhs.equals(i.lhs) && rhs.equals(i.rhs)
                    && dot == i.dot && lookahead.equals(i.lookahead);
        }
        @Override public int hashCode() {
            return Objects.hash(lhs, rhs, dot, lookahead);
        }
        public String nextSymbol() {
            return dot < rhs.size() ? rhs.get(dot) : null;
        }
        public Item moveDot() {
            return new Item(lhs, rhs, dot+1, lookahead);
        }
        @Override public String toString() {
            List<String> seg = new ArrayList<>(rhs);
            seg.add(dot, "•");
            return lhs + "→" + String.join(" ", seg) + "," + lookahead;
        }
    }

    public Parser() {
        defineGrammar();
        computeFirstSets();
        computeFollowSets();
        buildStates();
        buildParseTable();
    }

    private void defineGrammar() {
        // 增广文法
        addProduction(START_SYMBOL, Arrays.asList("program"));
        // 原始文法
        addProduction("program", Arrays.asList("block"));
        addProduction("block", Arrays.asList("{", "decls", "stmts", "}"));
        addProduction("decls", Arrays.asList("decls", "decl"));
        addProduction("decls", Arrays.asList());
        addProduction("decl", Arrays.asList("type", "id", ";"));
        addProduction("type", Arrays.asList("type", "[", "num", "]"));
        addProduction("type", Arrays.asList("basic"));
        addProduction("stmts", Arrays.asList("stmts", "stmt"));
        addProduction("stmts", Arrays.asList());
        addProduction("stmt", Arrays.asList("loc", "=", "bool", ";"));
        addProduction("stmt", Arrays.asList("if", "(", "bool", ")", "stmt"));
        addProduction("stmt", Arrays.asList("if", "(", "bool", ")", "stmt", "else", "stmt"));
        addProduction("stmt", Arrays.asList("while", "(", "bool", ")", "stmt"));
        addProduction("stmt", Arrays.asList("do", "stmt", "while", "(", "bool", ")", ";"));
        addProduction("stmt", Arrays.asList("break", ";"));
        addProduction("stmt", Arrays.asList("block"));
        addProduction("loc", Arrays.asList("loc", "[", "num", "]"));
        addProduction("loc", Arrays.asList("id"));
        addProduction("bool", Arrays.asList("bool", "||", "join"));
        addProduction("bool", Arrays.asList("join"));
        addProduction("join", Arrays.asList("join", "&&", "equality"));
        addProduction("join", Arrays.asList("equality"));
        addProduction("equality", Arrays.asList("equality", "==", "rel"));
        addProduction("equality", Arrays.asList("equality", "!=", "rel"));
        addProduction("equality", Arrays.asList("rel"));
        addProduction("rel", Arrays.asList("expr", "<", "expr"));
        addProduction("rel", Arrays.asList("expr", "<=", "expr"));
        addProduction("rel", Arrays.asList("expr", ">=", "expr"));
        addProduction("rel", Arrays.asList("expr", ">", "expr"));
        addProduction("rel", Arrays.asList("expr"));
        addProduction("expr", Arrays.asList("expr", "+", "term"));
        addProduction("expr", Arrays.asList("expr", "-", "term"));
        addProduction("expr", Arrays.asList("term"));
        addProduction("term", Arrays.asList("term", "*", "unary"));
        addProduction("term", Arrays.asList("term", "/", "unary"));
        addProduction("term", Arrays.asList("unary"));
        addProduction("unary", Arrays.asList("!", "unary"));
        addProduction("unary", Arrays.asList("-", "unary"));
        addProduction("unary", Arrays.asList("factor"));
        addProduction("factor", Arrays.asList("(", "bool", ")"));
        addProduction("factor", Arrays.asList("loc"));
        addProduction("factor", Arrays.asList("num"));
        addProduction("factor", Arrays.asList("real"));
        addProduction("factor", Arrays.asList("true"));
        addProduction("factor", Arrays.asList("false"));
        // 填充 terminals，nonTerminals
        for (String A : productions.keySet()) {
            nonTerminals.add(A);
            for (List<String> rhs : productions.get(A)) {
                for (String sym : rhs) {
                    if (!sym.isEmpty() && !productions.containsKey(sym)) {
                        terminals.add(sym);
                    }
                }
            }
        }
        terminals.add("$");  // EOF
    }

    private void addProduction(String lhs, List<String> rhs) {
        productions.computeIfAbsent(lhs, k -> new ArrayList<>()).add(rhs);
    }

    private void computeFirstSets() {
        // 初始化 FIRST
        for (String X : nonTerminals) {
            first.put(X, new HashSet<>());
        }
        for (String t : terminals) {
            first.put(t, new HashSet<>(Collections.singletonList(t)));
        }
        boolean changed;
        do {
            changed = false;
            for (String A : nonTerminals) {
                Set<String> fA = first.get(A);
                int before = fA.size();
                for (List<String> rhs : productions.get(A)) {
                    if (rhs.isEmpty()) {
                        fA.add("");
                    } else {
                        boolean nullable = true;
                        for (String sym : rhs) {
                            Set<String> fSym = first.get(sym);
                            fA.addAll(fSym);
                            if (!fSym.contains("")) {
                                nullable = false;
                                break;
                            }
                        }
                        if (nullable) {
                            fA.add("");
                        }
                    }
                }
                if (fA.size() > before) changed = true;
            }
        } while (changed);
    }

    private void computeFollowSets() {
        for (String A : nonTerminals) follow.put(A, new HashSet<>());
        follow.get(START_SYMBOL).add("$");
        boolean changed;
        do {
            changed = false;
            for (String A : nonTerminals) {
                for (List<String> rhs : productions.get(A)) {
                    for (int i = 0; i < rhs.size(); i++) {
                        String B = rhs.get(i);
                        if (nonTerminals.contains(B)) {
                            Set<String> f = follow.get(B);
                            int before = f.size();
                            Set<String> firstBeta = new HashSet<>();
                            boolean eps = true;
                            for (int j = i+1; j < rhs.size(); j++) {
                                Set<String> fb = first.get(rhs.get(j));
                                firstBeta.addAll(fb);
                                if (!fb.contains("")) { eps = false; break; }
                            }
                            if (eps) firstBeta.addAll(follow.get(A));
                            firstBeta.remove("");
                            f.addAll(firstBeta);
                            if (f.size() > before) changed = true;
                        }
                    }
                }
            }
        } while (changed);
    }

    private Set<Item> closure(Set<Item> I) {
        Set<Item> C = new HashSet<>(I);
        boolean added;
        do {
            added = false;
            for (Item it : new ArrayList<>(C)) {
                String B = it.nextSymbol();
                if (B != null && nonTerminals.contains(B)) {
                    List<String> beta_a = new ArrayList<>();
                    beta_a.addAll(it.rhs.subList(it.dot+1, it.rhs.size()));
                    beta_a.add(it.lookahead);
                    Set<String> firstBetaA = computeFirstSequence(beta_a);
                    for (List<String> rhsB : productions.get(B)) {
                        for (String la : firstBetaA) {
                            Item newIt = new Item(B, rhsB, 0, la);
                            if (!C.contains(newIt)) { C.add(newIt); added = true; }
                        }
                    }
                }
            }
        } while (added);
        return C;
    }

    /**
     * 计算一串符号序列的 FIRST 集合（不包括中间的 ε）
     */
    private Set<String> computeFirstSequence(List<String> seq) {
        Set<String> res = new HashSet<>();
        boolean allNullable = true;
        for (String X : seq) {
            Set<String> fX = first.get(X);
            if (fX == null) {
                throw new RuntimeException("No FIRST set for symbol: " + X);
            }
            // 加入非 ε 的符号
            for (String s : fX) {
                if (!s.isEmpty()) {
                    res.add(s);
                }
            }
            if (fX.contains("")) {
                // X 可推导 ε，继续处理下一个符号
            } else {
                allNullable = false;
                break;
            }
        }
        if (allNullable) {
            res.add("");
        }
        return res;
    }

    private Set<Item> gotoState(Set<Item> I, String X) {
        Set<Item> J = new HashSet<>();
        for (Item it : I) {
            if (X.equals(it.nextSymbol())) {
                J.add(it.moveDot());
            }
        }
        return closure(J);
    }

    private void buildStates() {
        Item startItem = new Item(START_SYMBOL, productions.get(START_SYMBOL).get(0), 0, "$" );
        Set<Item> I0 = closure(new HashSet<>(Collections.singletonList(startItem)));
        states.add(I0);
        boolean added;
        do {
            added = false;
            for (int i = 0; i < states.size(); i++) {
                Set<Item> I = states.get(i);
                Set<String> syms = new HashSet<>();
                for (Item it : I) {
                    String X = it.nextSymbol();
                    if (X != null) syms.add(X);
                }
                for (String X : syms) {
                    Set<Item> J = gotoState(I, X);
                    if (!J.isEmpty()) {
                        if (!states.contains(J)) {
                            states.add(J);
                            added = true;
                        }
                        int j = states.indexOf(J);
                        gotoTable.computeIfAbsent(i, k->new HashMap<>()).put(X, j);
                    }
                }
            }
        } while (added);
    }

    private void buildParseTable() {
        for (int i = 0; i < states.size(); i++) {
            actionTable.put(i, new HashMap<>());
            gotoTable.putIfAbsent(i, new HashMap<>());
            for (Item it : states.get(i)) {
                String A = it.lhs;
                List<String> rhs = it.rhs;
                if (it.dot < rhs.size()) {
                    String a = it.nextSymbol();
                    if (terminals.contains(a)) {
                        int s = gotoTable.get(i).get(a);
                        actionTable.get(i).put(a, new Action(Action.Type.SHIFT, s));
                    }
                } else {
                    if (A.equals(START_SYMBOL)) {
                        actionTable.get(i).put("$", new Action(Action.Type.ACCEPT, 0));
                    } else {
                        int prodIndex = getProductionIndex(A, rhs);
                        for (String la : Collections.singleton(it.lookahead)) {
                            actionTable.get(i).put(la, new Action(Action.Type.REDUCE, prodIndex));
                        }
                    }
                }
            }
        }
    }

    private int getProductionIndex(String A, List<String> rhs) {
        int idx = 0;
        for (Map.Entry<String, List<List<String>>> e : productions.entrySet()) {
            for (List<String> r : e.getValue()) {
                if (e.getKey().equals(A) && r.equals(rhs)) return idx;
                idx++;
            }
        }
        throw new RuntimeException("Production not found");
    }

    // 输出 LR(1) 语法分析表
    public void printParseTable() {
        System.out.println("===== LR(1) 语法分析表 =====");
        List<String> terms = new ArrayList<>(terminals);
        terms.remove("");
        Collections.sort(terms);
        List<String> nonterms = new ArrayList<>(nonTerminals);
        Collections.sort(nonterms);
        System.out.printf("State" + "%10s", "");
        for (String t : terms) System.out.printf("%8s", t);
        for (String A : nonterms) System.out.printf("%8s", A);
        System.out.println();
        for (int i = 0; i < states.size(); i++) {
            System.out.printf("%-5d", i);
            for (String t : terms) {
                Action act = actionTable.get(i).get(t);
                System.out.printf("%8s", act == null ? "" : act);
            }
            for (String A : nonterms) {
                Integer g = gotoTable.get(i).get(A);
                System.out.printf("%8s", g == null ? "" : g);
            }
            System.out.println();
        }
    }

    // 使用 Lexer 生成的 token 序列, 输出分析栈内容
    public void parse(List<Lexer.Token> tokens) {
        System.out.println("===== LR(1) 分析过程 =====");
        Stack<Integer> stateStack = new Stack<>();
        Stack<String> symbolStack = new Stack<>();
        stateStack.push(0);
        symbolStack.push("$");
        tokens.add(new Lexer.Token(Lexer.TokenType.EOF, "", tokens.get(tokens.size()-1).line));
        int idx = 0;
        while (true) {
            int s = stateStack.peek();
            String a = tokens.get(idx).value.isEmpty() ? "$" : tokens.get(idx).value;
            Action act = actionTable.get(s).get(a);
            // 打印栈和输入
            printStacks(stateStack, symbolStack, tokens, idx, act);
            if (act == null) {
                System.err.println("Error: no action for state " + s + ", symbol " + a);
                return;
            }
            switch (act.type) {
                case SHIFT:
                    symbolStack.push(a);
                    stateStack.push(act.value);
                    idx++;
                    break;
                case REDUCE:
                    Map.Entry<String, List<List<String>>> prod = getProductionByIndex(act.value);
                    List<String> rhs = prod.getValue().get(getRhsIndex(act.value));
                    for (int i = 0; i < rhs.size(); i++) {
                        symbolStack.pop(); stateStack.pop();
                    }
                    symbolStack.push(prod.getKey());
                    int t = stateStack.peek();
                    int g = gotoTable.get(t).get(prod.getKey());
                    stateStack.push(g);
                    break;
                case ACCEPT:
                    System.out.println("ACCEPT");
                    return;
            }
        }
    }

    private void printStacks(Stack<Integer> ss, Stack<String> syms, List<Lexer.Token> tokens, int idx, Action act) {
        System.out.printf("%-30s%-30s%-10s\n", ss + "    " + syms, tokens.subList(idx, tokens.size()), act);
    }

    private Map.Entry<String, List<List<String>>> getProductionByIndex(int idx) {
        int count = 0;
        for (Map.Entry<String, List<List<String>>> e : productions.entrySet()) {
            for (List<String> rhs : e.getValue()) {
                if (count == idx) return e;
                count++;
            }
        }
        throw new RuntimeException("Invalid production index");
    }

    private int getRhsIndex(int idx) {
        int count = 0;
        for (List<List<String>> lst : productions.values()) {
            for (int i = 0; i < lst.size(); i++) {
                if (count++ == idx) return i;
            }
        }
        throw new RuntimeException("Invalid production index");
    }

    public static void main(String[] args) throws Exception {
        try (InputStream input = new FileInputStream("input.txt")) {
            Lexer lexer = new Lexer(input);
            List<Lexer.Token> tokens = new ArrayList<>();
            Lexer.Token tok;
            do { tok = lexer.nextToken(); tokens.add(tok); } while (tok.type != Lexer.TokenType.EOF);
            Parser parser = new Parser();
            parser.printParseTable();
            parser.parse(tokens);
        }
    }
}
