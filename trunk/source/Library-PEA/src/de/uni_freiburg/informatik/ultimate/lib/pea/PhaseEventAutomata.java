/* $Id: PhaseEventAutomata.java 409 2009-07-20 14:54:16Z jfaber $
 *
 * This file is part of the PEA tool set
 *
 * The PEA tool set is a collection of tools for
 * Phase Event Automata (PEA). See
 * http://csd.informatik.uni-oldenburg.de/projects/peatools.html
 * for more information.
 *
 * Copyright (C) 2005-2006, Department for Computing Science,
 *                          University of Oldenburg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package de.uni_freiburg.informatik.ultimate.lib.pea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import de.uni_freiburg.informatik.ultimate.lib.pea.reqcheck.PEAPhaseIndexMap;
import de.uni_freiburg.informatik.ultimate.lib.pea.util.SimpleSet;

public class PhaseEventAutomata implements Comparable<Object> {

	public static final String TIMES = "_X_";
	protected final String mName;
	protected final List<Phase> mPhases;
	protected final List<InitialTransition> mInit;
	protected final List<String> mClocks;

	// A map of variables and its types to be used in this PEA.
	protected final Map<String, String> mVariables;

	// The set of events used in the PEA.
	protected final Set<String> mEvents;

	// Additional declarations needed when processing this PEA.
	protected List<String> mDeclarations;

	public PhaseEventAutomata(final String name, final List<Phase> phases, final List<InitialTransition> init) {
		this(name, phases, init, new ArrayList<String>());
	}

	public PhaseEventAutomata(final String name, final List<Phase> phases, final List<InitialTransition> init,
			final Map<String, String> variables) {
		this(name, phases, init, Collections.emptyList(), variables, null, null);
	}

	public PhaseEventAutomata(final String name, final List<Phase> phases, final List<InitialTransition> init,
			final List<String> clocks, final Map<String, String> variables) {
		this(name, phases, init, clocks, variables, null, null);
	}

	public PhaseEventAutomata(final String name, final List<Phase> phases, final List<InitialTransition> init,
			final List<String> clocks) {
		this(name, phases, init, clocks, null, null);
	}

	public PhaseEventAutomata(final String name, final List<Phase> phases, final List<InitialTransition> init,
			final List<String> clocks, final Map<String, String> variables, final List<String> declarations) {
		this(name, phases, init, clocks, variables, null, declarations);
	}

	/**
	 * @param clocks
	 * @param declarations
	 * @param init
	 * @param name
	 * @param phases
	 * @param variables
	 */
	public PhaseEventAutomata(final String name, final List<Phase> phases, final List<InitialTransition> init,
			final List<String> clocks, final Map<String, String> variables, final Set<String> events,
			final List<String> declarations) {
		if (clocks == null) {
			mClocks = new ArrayList<>();
		} else {
			mClocks = clocks;
		}
		mEvents = events;
		mDeclarations = declarations;
		mInit = init;
		mName = name;
		mPhases = phases;
		mVariables = variables;

		// add initial transition to Phases in initPhases
		// TODO: remove this, either store all edges in the phases (would be clear) or in the pea, but not both.
		for (final InitialTransition initTrans : init) {
			initTrans.getDest().setInitialTransition(initTrans);
		}
	}

	public PhaseEventAutomata parallel(final PhaseEventAutomata b) {
		if (b instanceof PEATestAutomaton) {
			return b.parallel(this);
		}
		final List<Phase> newInit = new ArrayList<>();
		final TreeMap<String, Phase> newPhases = new TreeMap<>();

		class TodoEntry {
			Phase p1, p2, p;

			TodoEntry(final Phase p1, final Phase p2, final Phase p) {
				this.p1 = p1;
				this.p2 = p2;
				this.p = p;
			}
		}

		final List<TodoEntry> todo = new LinkedList<>();

		for (int i = 0; i < mInit.size(); i++) {
			for (int j = 0; j < b.mInit.size(); j++) {
				final CDD sinv = mInit.get(i).getDest().getStateInv().and(b.mInit.get(j).getDest().getStateInv());
				if (sinv != CDD.FALSE) {
					final CDD cinv = mInit.get(i).getDest().getClockInv().and(b.mInit.get(j).getDest().getClockInv());
					final Phase p = new Phase(
							mInit.get(i).getDest().getName() + TIMES + b.mInit.get(j).getDest().getName(), sinv, cinv);

					newInit.add(p);
					newPhases.put(p.getName(), p);
					todo.add(new TodoEntry(mInit.get(i).getDest(), b.mInit.get(j).getDest(), p));
				}
			}
		}
		while (!todo.isEmpty()) {
			final TodoEntry entry = todo.remove(0);
			final CDD srcsinv = entry.p1.getStateInv().and(entry.p2.getStateInv());
			final Iterator<?> i = entry.p1.getTransitions().iterator();
			while (i.hasNext()) {
				final Transition t1 = (Transition) i.next();
				final Iterator<?> j = entry.p2.getTransitions().iterator();
				while (j.hasNext()) {
					final Transition t2 = (Transition) j.next();

					final CDD guard = t1.getGuard().and(t2.getGuard());
					if (guard == CDD.FALSE) {
						continue;
					}
					final CDD sinv = t1.getDest().getStateInv().and(t2.getDest().getStateInv());
					// This leads to a serious bug -
					// if (sinv.and(guard) == CDD.FALSE)
					if ((sinv == CDD.FALSE) || (guard != CDD.TRUE && srcsinv.and(guard).and(sinv.prime(Collections.emptySet())) == CDD.FALSE)) {
						// TODO: Overapproximating for BoogieDecisions because constants will become primed
						continue;
					}
					final CDD cinv = t1.getDest().getClockInv().and(t2.getDest().getClockInv());
					final String[] resets = new String[t1.getResets().length + t2.getResets().length];
					System.arraycopy(t1.getResets(), 0, resets, 0, t1.getResets().length);
					System.arraycopy(t2.getResets(), 0, resets, t1.getResets().length, t2.getResets().length);
					final Set<String> stoppedClocks = new SimpleSet<>(
							t1.getDest().getStoppedClocks().size() + t2.getDest().getStoppedClocks().size());
					stoppedClocks.addAll(t1.getDest().getStoppedClocks());
					stoppedClocks.addAll(t2.getDest().getStoppedClocks());

					final String newname = t1.getDest().getName() + TIMES + t2.getDest().getName();
					Phase p = newPhases.get(newname);

					if (p == null) {
						p = new Phase(newname, sinv, cinv, stoppedClocks);
						newPhases.put(newname, p);
						todo.add(new TodoEntry(t1.getDest(), t2.getDest(), p));
					}
					entry.p.addTransition(p, guard, resets);
				}
			}
		}

		final Phase[] allPhases = newPhases.values().toArray(new Phase[newPhases.size()]);
		final Phase[] initPhases = newInit.toArray(new Phase[newInit.size()]);
		final List<InitialTransition> initTransitions = new ArrayList<>();

		// add initial transition to Phases in initPhases
		for (final Phase phase : initPhases) {
			final InitialTransition initialTransition = new InitialTransition(phase.getClockInv(), phase);
			phase.setInitialTransition(initialTransition);
			initTransitions.add(initialTransition);
		}

		final List<String> newClocks = mergeClockLists(b);

		final Map<String, String> newVariables = mergeVariableLists(b);

		final List<String> newDeclarations = mergeDeclarationLists(b);

		return new PhaseEventAutomata(mName + TIMES + b.mName, Arrays.asList(allPhases), initTransitions, newClocks,
				newVariables, newDeclarations);
	}

	/**
	 * Merges the declaration lists of this automata and the given automata b and returns a new list containing the
	 * result.
	 *
	 * @param b
	 *            automata containing the list to be merged
	 * @return merged list
	 */
	protected List<String> mergeDeclarationLists(final PhaseEventAutomata b) {
		// Merge declarations
		List<String> newDeclarations;
		if (mDeclarations == null) {
			newDeclarations = b.getDeclarations();
		} else if (b.getDeclarations() == null) {
			newDeclarations = mDeclarations;
		} else {
			newDeclarations = new ArrayList<>(mDeclarations);
			newDeclarations.addAll(b.getDeclarations());
		}
		return newDeclarations;
	}

	/**
	 * Merges the variable lists of this automata and the given automata b and returns a new list containing the merge
	 * result.
	 *
	 * @param b
	 *            automata containing the list to be merged
	 * @return merged list
	 */
	protected Map<String, String> mergeVariableLists(final PhaseEventAutomata b) {
		// Merge variable lists
		final Map<String, String> newVariables;
		if (mVariables == null) {
			newVariables = b.getVariables();
		} else if (b.getVariables() == null) {
			newVariables = mVariables;
		} else {
			newVariables = new HashMap<>();
			for (final String var : mVariables.keySet()) {
				if (b.getVariables().containsKey(var) && !b.getVariables().get(var).equals(mVariables.get(var))) {
					throw new RuntimeException("Different type definitions of " + var + "found!");
				}
				newVariables.put(var, mVariables.get(var));
			}
			newVariables.putAll(b.getVariables());
		}
		return newVariables;
	}

	/**
	 * Merges the clock lists of this automata and the given automata b and returns a new list containing the merge
	 * result.
	 *
	 * @param b
	 *            automata containing the list to be merged
	 * @return merged list
	 */
	protected List<String> mergeClockLists(final PhaseEventAutomata b) {
		// Merge clock lists
		final List<String> newClocks = new ArrayList<>(mClocks);
		newClocks.addAll(b.getClocks());
		return newClocks;
	}

	@Override
	public String toString() {
		return mName;
	}

	/**
	 * @return Returns the init.
	 */
	public List<Phase> getInit() {
		final List<Phase> initPhases = new ArrayList<>();
		for (final InitialTransition t : mInit) {
			initPhases.add(t.getDest());
		}
		return initPhases;
	}

	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return mName;
	}

	/**
	 * @return Returns the phases.
	 */
	public List<Phase> getPhases() {
		return mPhases;
	}

	public List<String> getClocks() {
		return mClocks;
	}

	/**
	 * @return Returns the variables.
	 */
	public Map<String, String> getVariables() {
		return mVariables;
	}

	/**
	 * @return Returns the variables.
	 */
	public Set<String> getEvents() {
		return mEvents;
	}

	/**
	 * @return Returns the declarations.
	 */
	public List<String> getDeclarations() {
		return mDeclarations;
	}

	public void addToClocks(final String clock) {
		mClocks.add(clock);
	}

	@Override
	public int compareTo(final Object o) {
		return mName.compareTo(((PhaseEventAutomata) o).mName);
	}

	public boolean isEmpty() {
		return getPhases().size() <= 0;
	}

	public int getNumberOfLocations() {
		return getPhases().size();
	}

	public Phase getLocation(final int i) {
		return getPhases().get(i);
	}

	public void rename() {
		final int locCounter = getNumberOfLocations();
		int counter = 0; // der ist nur aus technischen Gründen da: wenn wir zwei zustande st0Xst2 und st2Xst1 haben
		// dann würden sonst beide auf st3 umbenannt - das wollen wir nicht, daher dieser counter dazu
		for (int i = 0; i < locCounter; i++) {
			final Phase location = getLocation(i);
			final String[] result = splitForComponents(location.getName());
			int maxIndex = 0;
			for (final String compName : result) {
				final PEAPhaseIndexMap map = new PEAPhaseIndexMap(compName);
				if (maxIndex < map.getIndex() - 1) {
					maxIndex = map.getIndex() - 1;
				}
			}
			final String newName = "st" + counter + maxIndex;
			counter++;
			location.setName(newName);
		}
	}

	// Splitted einen String location auf, so dass alle Teile, die durch X abgetrennt sind,
	// in einen neuen ArrayString gepackt werden
	private static String[] splitForComponents(final String location) {
		// Create a pattern to match breaks
		final Pattern p = Pattern.compile("_X_");
		return p.split(location);
	}

	// diese Methode vereinfacht die Guards eines PEAS
	// Bsp Guard (A or B) und Stateinvariante des Folgezustands ist (neg B) dann
	// wird der Guard vereinfacht zu (A)
	public void simplifyGuards() {

		final List<Phase> phases = getPhases();
		for (final Phase phase : phases) {
			final List<Transition> transitions = phase.getTransitions();
			for (final Transition trans : transitions) {
				trans.simplifyGuard();
			}
		}
	}

	public boolean isStrict() {
		for (final Phase phase : mPhases) {
			if (phase.isStrict() || !phase.getModifiedConstraints().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public boolean isTotalised() {
		return mName.endsWith(PEAComplement.TOTAL_POSTFIX);
	}

	public boolean isComplemented() {
		return mName.endsWith(PEAComplement.COMPLEMENT_POSTFIX);
	}
}
