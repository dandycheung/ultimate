/*
 * Copyright (C) 2020 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2020 University of Freiburg
 *
 * This file is part of the ULTIMATE PEAtoBoogie plug-in.
 *
 * The ULTIMATE PEAtoBoogie plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE PEAtoBoogie plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE PEAtoBoogie plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE PEAtoBoogie plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE PEAtoBoogie plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.pea2boogie.results;

import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.results.AbstractResultAtElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;

public class ReqCheckFailResult<E extends IElement> extends AbstractResultAtElement<E> {

	private final ReqCheck mReqCheck;

	public ReqCheckFailResult(final E element, final String plugin) {
		super(element, plugin);
		mReqCheck = (ReqCheck) Check.getAnnotation(element);
	}

	@Override
	public String getShortDescription() {
		return mReqCheck.getNegativeMessage();
	}

	@Override
	public String getLongDescription() {
		return mReqCheck.getNegativeMessage();
	}

	public ReqCheck getCheck() {
		return mReqCheck;
	}

}