/* $Id$
 *
 * This file is part of the PEA tool set
 *
 * The PEA tool set is a collection of tools for Phase Event Automata
 * (PEA).
 *
 * Copyright (C) 2005-2006, Carl von Ossietzky University of Oldenburg
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

package de.uni_freiburg.informatik.ultimate.lib.pea.modelchecking;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.lib.pea.CDD;
import de.uni_freiburg.informatik.ultimate.lib.pea.Decision;

/**
 * TCSWriter is an abstract class providing common functionality for writing Transition Constraint Systems into a file
 * or multiple files. The Transition Constraint Systems are generated by a corresponding PEA2TCSConverter.
 *
 * @author jfaber
 * @see de.uni_freiburg.informatik.ultimate.lib.pea.modelchecking.PEA2TCSConverter
 */
public abstract class TCSWriter {

	protected String mFileName;

	protected PEA2TCSConverter mConverter;

	/**
	 * @return Returns the converter.
	 */
	public PEA2TCSConverter getConverter() {
		return mConverter;
	}

	/**
	 * @param converter
	 *            The converter to set.
	 */
	public void setConverter(final PEA2TCSConverter converter) {
		mConverter = converter;
	}

	/**
	 * @param fileName
	 */
	public TCSWriter(final String fileName) {
		mFileName = fileName;
	}

	/**
	 * @param mConverter
	 */
	public abstract void write();

	/**
	 * Writes a CDD that represents a conjunction into a specific output format. Therefore it uses abstract methods
	 * writeAndDelimiter() and writeDecision(), which have to be implemented for every desired output.
	 *
	 * @param constraint
	 *            The CDD constraint that has to be written. The constraint is not allowed to be equal to false. This
	 *            causes an <code>IllegalArgumentException</code>.
	 * @param writer
	 *            The CDD constraint is written to this writer.
	 */
	protected void writeConjunction(final CDD constraint, final Writer writer) throws IOException {

		if (constraint == CDD.TRUE) {
			return;
		}
		if (constraint == CDD.FALSE) {
			throw new IllegalArgumentException("A constraint equal to false not allowed here");
		}

		for (int i = 0; i < constraint.getChilds().length; i++) {
			if (constraint.getChilds()[i] == CDD.FALSE) {
				continue;
			}

			writeDecision(constraint.getDecision(), i, writer);
			writeAndDelimiter(writer);

			writeConjunction(constraint.getChilds()[i], writer);

		}

	}

	/**
	 * Writes an And delimiter to the output file. Abstract method has to be overwritten to match domain specific And
	 * symbols.
	 *
	 * @param writer
	 *            The and delimiter is written to this writer object.
	 * @throws IOException
	 */
	protected abstract void writeAndDelimiter(Writer writer) throws IOException;

	/**
	 * This method writes a decision from a given CDD into an Writer, usually a FileWriter. The exact representation of
	 * a decision crucially depends on the target format so this method should be implemented for any specific output
	 * language.
	 *
	 * @param decision
	 *            The decision that shall be written.
	 * @param child
	 *            This decision occurs in a CDD as the child given here.
	 * @param writer
	 *            The writer for the result of this operation.
	 */
	protected abstract void writeDecision(Decision<?> decision, int child, Writer writer) throws IOException;

	/**
	 * TODO JF: comment
	 *
	 * @param declarations
	 * @param variables
	 * @param globalInvariant
	 */
	protected CDD processDeclarations(final List<String> declarations, final Map<String, String> variables) {
		// implement in subclass if needed
		return CDD.TRUE;
	}

}
