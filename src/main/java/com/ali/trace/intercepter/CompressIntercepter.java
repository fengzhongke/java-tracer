package com.ali.trace.intercepter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.ali.trace.util.NameUtils;

/**
 * delete duplication stacks
 * 
 * @author hanlang.hl
 *
 */
public class CompressIntercepter extends BaseIntercepter {
	private final ThreadLocal<Integer> t_depth = new ThreadLocal<Integer>();
	private final ThreadLocal<Integer> t_seq = new ThreadLocal<Integer>();
	private final ThreadLocal<Map<String, Node>> t_map = new ThreadLocal<Map<String, Node>>();
	private final ThreadLocal<Node> t_root = new ThreadLocal<Node>();
	private final ThreadLocal<Stack<Node>> t_stack = new ThreadLocal<Stack<Node>>();

	public CompressIntercepter(String path) {
		super(path);
	}

	public final void start(String c, String m) throws Exception {
		if (t_seq.get() == null) {
			t_seq.set(0);
			t_map.set(new HashMap<String, Node>());
			t_stack.set(new Stack<Node>());
		}

		Integer depth = t_depth.get();
		t_depth.set(depth = (depth == null) ? 1 : depth + 1);
		Node node = new Node(m, c).buildDepth(depth);
		if (t_stack.get().isEmpty()) {
			t_stack.get().push(node);
			t_root.set(node);
		} else {
			Node preNode = t_stack.get().peek();
			if (preNode.depth == depth - 1) {
				preNode.sons.add(node);
				node.parent = preNode;
				t_stack.get().push(node);
			} else if (preNode.depth > depth - 1) {
				do {
					preNode = t_stack.get().pop();
					preNode.format();
					if (preNode.parent != null && preNode.parent.isRef == 1) {
						write(t_root.get().printPre().append(preNode.printSur()).toString());
						preNode.sons.clear();
					}
				} while (preNode.depth > depth);
				Node parent = t_stack.get().peek();
				parent.sons.add(node);
				node.parent = parent;
				t_stack.get().push(node);
			} else {
				throw new RuntimeException("depth error");
			}
		}
	}

	public final void end(String c, String m) throws Exception {
		t_depth.set(t_depth.get() - 1);
		if (t_depth.get() == 0) {
			while (!t_stack.get().isEmpty()) {
				Node preNode = t_stack.get().pop();
				preNode.format();
				if (preNode.parent != null && preNode.parent.isRef == 1) {
					write(t_root.get().printPre().append(preNode.printSur()).toString());
					preNode.sons.clear();
				}
			}
			write(t_root.get().printSur().toString());
		}
	}

	class Node {
		String method;
		String clasz;
		Node parent;
		int depth;

		String content;
		int ref = 0;
		int isRef = 0;
		int print = 0;
		List<Node> sons = new ArrayList<Node>();

		public Node(String method, String clasz) {
			this.method = method;
			this.clasz = clasz;
		}

		public Node buildDepth(int depth) {
			this.depth = depth;
			return this;
		}

		public void format() {
			if (content == null) {
				StringBuilder sb = new StringBuilder(clasz).append(".").append(method).append("[");
				Set<String> set = new HashSet<String>();
				Iterator<Node> itr = sons.iterator();
				while (itr.hasNext()) {
					Node node = itr.next();
					node.format();
					if (set.add(node.content)) {
						sb.append(node.content).append(",");
					} else {
						itr.remove();
					}
				}
				sb.append("]");
				content = NameUtils.getHexMd5(sb.toString());
				Node refNode = t_map.get().get(content);
				if (refNode == null) {
					t_map.get().put(content, this);
					t_seq.set(t_seq.get() + 1);
					this.ref = t_seq.get();
					setRef(1);
				} else {
					this.ref = refNode.ref;
					setRef(2);
				}
			}
		}

		public void setRef(int isRef) {
			if (this.isRef == 0) {
				this.isRef = isRef;
				if (isRef == 1 && parent != null) {
					parent.setRef(isRef);
				}
			}
		}

		public StringBuilder printPre() {
			StringBuilder sb = new StringBuilder();
			if (print == 0) {
				sb.append("<").append(method).append(" c='").append(clasz.substring(clasz.lastIndexOf(".") + 1))
						.append("'>\r\n").toString();
				print = 1;
				if (isRef == 2) {
					sb.append(printSur());
				}
			}
			if (isRef == 1) {
				Set<String> preSons = new HashSet<String>();
				for (Node son : sons) {
					if (!preSons.contains(son.content)) {
						preSons.add(son.content);
						sb.append(son.printPre());
					}
				}
			}
			return sb;
		}

		public StringBuilder printSur() {
			if (print == 1) {
				print = 2;
				return new StringBuilder("</").append(method).append(" r='").append(ref).append("' i='").append(isRef)
						.append("'").append(">\r\n");
			}
			return new StringBuilder();
		}
	}

}
