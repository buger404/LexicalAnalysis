import java.io.*;
import java.util.*;

public class Lexer {
    private static final int BUFFER_SIZE = 4096;
    private final char[] buffer = new char[BUFFER_SIZE];
    private int bufferPos = 0;
    private int bufferLimit = 0;
    private boolean eof = false;
    
    private final BufferedReader reader;
    private int currentLine = 1;
    
    // 简化后的Token类型
    public enum TokenType {
        // 合并关键字和标识符处理
        KEYWORD_IF, KEYWORD_ELSE, KEYWORD_WHILE, KEYWORD_DO, KEYWORD_BREAK,
        KEYWORD_INT, KEYWORD_FLOAT, KEYWORD_BOOL, KEYWORD_TRUE, KEYWORD_FALSE,
        IDENTIFIER,
        
        // 合并所有运算符
        OPERATOR,
        
        // 数值类型
        INTEGER, REAL,
        
        // 分隔符
        DELIMITER,
        
        // 特殊类型
        EOF, ERROR
    }

    private static final Map<String, TokenType> KEYWORDS = Map.of(
        "if", TokenType.KEYWORD_IF, "else", TokenType.KEYWORD_ELSE,"while", TokenType.KEYWORD_WHILE,
        "do", TokenType.KEYWORD_DO, "break", TokenType.KEYWORD_BREAK,
        "int", TokenType.KEYWORD_INT, "float", TokenType.KEYWORD_FLOAT, "bool", TokenType.KEYWORD_BOOL,
        "true", TokenType.KEYWORD_TRUE, "false", TokenType.KEYWORD_FALSE
    );

    public static class Token {
        public final TokenType type;
        public final String value;
        public final int line;

        public Token(TokenType type, String value, int line) {
            this.type = type;
            this.value = value;
            this.line = line;
        }

        @Override
        public String toString() {
            return String.format("(%s, \"%s\") line %d", type, value, line);
        }
    }

    public Lexer(InputStream input) {
        this.reader = new BufferedReader(new InputStreamReader(input));
        fillBuffer();
    }

    private void fillBuffer() {
        try {
            bufferLimit = reader.read(buffer);
            if (bufferLimit == -1) eof = true;
            bufferPos = 0;
        } catch (IOException e) {
            error("Buffer read error: " + e.getMessage());
        }
    }

    private char nextChar() {
        if (eof) return Character.MAX_VALUE;
        
        if (bufferPos >= bufferLimit) {
            fillBuffer();
            if (eof) return Character.MAX_VALUE;
        }
        
        char ch = buffer[bufferPos++];
        if (ch == '\n') currentLine++;
        return ch;
    }

    private char peekChar() {
        if (eof) return Character.MAX_VALUE;
        if (bufferPos < bufferLimit) return buffer[bufferPos];
        
        try {
            fillBuffer();
            return eof ? Character.MAX_VALUE : buffer[bufferPos];
        } catch (Exception e) {
            return Character.MAX_VALUE;
        }
    }

    public Token nextToken() {
        StringBuilder lexeme = new StringBuilder();
        int startLine = currentLine;
        char ch = skipWhitespaceAndComments();

        if (ch == Character.MAX_VALUE) {
            return new Token(TokenType.EOF, "", currentLine);
        }

        // 标识符或关键字
        if (Character.isLetter(ch)) {
            return readIdentifier(ch, startLine);
        }
        // 数字
        else if (Character.isDigit(ch)) {
            return readNumber(ch, startLine);
        }
        // 运算符
        else if (isOperatorChar(ch)) {
            return readOperator(ch, startLine);
        }
        // 分隔符
        else if (isDelimiter(ch)) {
            lexeme.append(ch);
            return new Token(TokenType.DELIMITER, lexeme.toString(), startLine);
        }
        
        // 错误处理
        lexeme.append(ch);
        error("Invalid character: " + ch);
        return new Token(TokenType.ERROR, lexeme.toString(), startLine);
    }

    private char skipWhitespaceAndComments() {
        char ch = nextChar();
        while (true) {
            // 跳过空白
            while (Character.isWhitespace(ch)) {
                ch = nextChar();
            }
            
            // 处理单行注释
            if (ch == '/' && peekChar() == '/') {
                while (ch != '\n' && ch != Character.MAX_VALUE) {
                    ch = nextChar();
                }
            } else {
                break;
            }
        }
        return ch;
    }

    private Token readIdentifier(char firstChar, int line) {
        StringBuilder lexeme = new StringBuilder().append(firstChar);
        char ch = nextChar();
        
        while (Character.isLetterOrDigit(ch)) {
            lexeme.append(ch);
            ch = nextChar();
        }
        
        // 如果不是EOF，回退一个字符
        if (ch != Character.MAX_VALUE) {
            bufferPos--;
        }
        
        String id = lexeme.toString();
        return new Token(KEYWORDS.getOrDefault(id, TokenType.IDENTIFIER), id, line);
    }

    private Token readNumber(char firstChar, int line) {
        StringBuilder lexeme = new StringBuilder().append(firstChar);
        boolean isReal = false;
        char ch = nextChar();
        
        while (Character.isDigit(ch) || ch == '.') {
            if (ch == '.') {
                if (isReal) {
                    error("Invalid number format");
                    return new Token(TokenType.ERROR, lexeme.toString(), line);
                }
                isReal = true;
            }
            lexeme.append(ch);
            ch = nextChar();
        }
        
        if (ch != Character.MAX_VALUE) {
            bufferPos--;
        }
        
        return new Token(isReal ? TokenType.REAL : TokenType.INTEGER, 
                        lexeme.toString(), line);
    }

    private Token readOperator(char firstChar, int line) {
        StringBuilder lexeme = new StringBuilder().append(firstChar);
        char next = peekChar();
        
        // 检查双字符运算符
        if (isOperatorChar(next)) {
            String potentialOp = lexeme.toString() + next;
            if (isValidOperator(potentialOp)) {
                lexeme.append(nextChar());
            }
        }
        
        return new Token(TokenType.OPERATOR, lexeme.toString(), line);
    }

    private boolean isOperatorChar(char c) {
        return "+-*/%=><!&|".indexOf(c) >= 0;
    }

    private boolean isValidOperator(String op) {
        return op.length() == 2 && "|| && == != <= >= ++ --".contains(op);
    }

    private boolean isDelimiter(char c) {
        return "{}();[],".indexOf(c) >= 0;
    }

    private void error(String message) {
        System.err.println("Error at line " + currentLine + ": " + message);
    }

    public static void main(String[] args) {
        try (InputStream input = new FileInputStream("input.txt");
             PrintWriter writer = new PrintWriter("output.txt")) {
            
            Lexer lexer = new Lexer(input);
            writer.println("========== Token Stream ==========");
            
            Token token;
            do {
                token = lexer.nextToken();
                writer.println(token);
            } while (token.type != TokenType.EOF);
            
            System.out.println("Lexical analysis completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}