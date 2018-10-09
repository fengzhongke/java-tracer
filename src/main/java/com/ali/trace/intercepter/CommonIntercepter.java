package com.ali.trace.intercepter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

public class CommonIntercepter extends BaseIntercepter {

	private final long start = System.currentTimeMillis();
	private final ThreadLocal<Piece> prePiece = new ThreadLocal<Piece>();
	private final ThreadLocal<Long> lineNumber = new ThreadLocal<Long>();
	private final ThreadLocal<Long> preTime = new ThreadLocal<Long>();
	private final ThreadLocal<Integer> stack = new ThreadLocal<Integer>();
	private final ThreadLocal<Stack<Piece>> pieceStacks = new ThreadLocal<Stack<Piece>>();

	private static final char TIME_OFF_START = 'o';
	private static final char THREAD_LINE = 'l';
	private static final char THREAD_DEPTH = 'd';
	private static final char CLASS_NAME = 'c';
	private static final char STEP_COST = 't';
	private static final char SON_COUNT = 's';
	private static Map<Character, String> INFO_MAP = new HashMap<Character, String>();
	static {
		Field[] fields = CommonIntercepter.class.getDeclaredFields();
		for (Field field : fields) {
			if (field.getType() == char.class) {
				try {
					INFO_MAP.put((Character) field.get(null), field.getName());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public CommonIntercepter(String path) {
		super(path);
	}

	@Override
	public void start(String c, String m) {
		try {
			Integer s = stack.get();
			StringBuilder sb = null;
			if (s == null) {
				sb = getBuilder(m);
				stack.set(1);
			} else {
				sb = new StringBuilder(64).append('<').append(m);
				stack.set(s + 1);
			}
			printTime(printDepth(printLine(sb)));
			sb.append(" ").append(CLASS_NAME).append("='").append(c).append('\'').append(">\r\n");

			write(sb.toString());
			Piece piece = new Piece(c, m, 0L);
			prePiece.set(piece);
			if (!pieceStacks.get().isEmpty()) {
				Piece superPiece = pieceStacks.get().peek();
				superPiece.sons = superPiece.sons + 1;
			}
			pieceStacks.get().push(piece);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	@Override
	public void end(String c, String m) {
		try {
			stack.set(stack.get() - 1);
			Stack<Piece> pieceStack = pieceStacks.get();
			Piece piece = null;
			if (pieceStack == null || pieceStack.isEmpty() || (piece = pieceStack.pop()) == null || !c.equals(piece.c)
					|| !m.equals(piece.m)) {
				return;
			}

			StringBuilder sb = new StringBuilder("</").append(m);
			printSons(printCost(sb, piece), piece);
			sb.append(">\r\n");
			write(sb.toString());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	StringBuilder getBuilder(String m) throws IOException {
		lineNumber.set(0L);
		Stack<Piece> pieceStack = pieceStacks.get();
		if (pieceStack == null) {
			pieceStacks.set(new Stack<Piece>());
		}
		StringBuilder sb = new StringBuilder(96).append('<').append(m);
		for (Entry<Character, String> entry : INFO_MAP.entrySet()) {
			sb.append(" ").append(entry.getKey()).append("_").append("='").append(entry.getValue()).append('\'');
		}
		return sb;
	}

	long printTime(StringBuilder sb) {
		long t = System.currentTimeMillis() - start;
		preTime.set(t);
		sb.append(" ").append(TIME_OFF_START).append("='").append(t).append('\'');
		return t;
	}

	StringBuilder printLine(StringBuilder sb) {
		long l = lineNumber.get() + 1;
		lineNumber.set(l);
		return sb.append(" ").append(THREAD_LINE).append("='").append(l).append('\'');
	}

	StringBuilder printDepth(StringBuilder sb) {
		return sb.append(" ").append(THREAD_DEPTH).append("='").append(stack.get()).append('\'');
	}

	StringBuilder printCost(StringBuilder sb, Piece piece) {
		long p = System.currentTimeMillis() - start - piece.time;
		return sb.append(" ").append(STEP_COST).append("='").append(p).append('\'');
	}

	StringBuilder printSons(StringBuilder sb, Piece piece) {
		return sb.append(" ").append(SON_COUNT).append("='").append(piece.sons).append('\'');
	}

	/**
	 * record method stack .
	 */
	static final class Piece {
		String c;
		String m;
		long time;
		int sons;

		Piece(String c, String m, long time) {
			this.c = c;
			this.m = m;
			this.time = time;
			this.sons = 0;
		}
	}
}
