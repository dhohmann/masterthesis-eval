package io.github.dhohmann.javasmt;

import org.spldev.formula.expression.atomic.Atomic;
import org.spldev.util.tree.structure.Tree;
import org.spldev.util.tree.visitor.TreeVisitor;

import java.util.List;

public class LiteralsCounter implements TreeVisitor<Integer, Tree<?>> {

	private int literals = 0;

	@Override
	public void reset() {
		literals = 0;
	}

	@Override
	public VisitorResult firstVisit(List<Tree<?>> path) {
		if (TreeVisitor.getCurrentNode(path) instanceof Atomic)
			literals++;
		return VisitorResult.Continue;
	}

	@Override
	public Integer getResult() {
		return literals;
	}
}
