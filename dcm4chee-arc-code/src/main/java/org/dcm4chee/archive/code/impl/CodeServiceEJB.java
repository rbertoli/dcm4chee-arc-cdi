/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011-2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.code.impl;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.dcm4chee.archive.code.CodeService;
import org.dcm4chee.archive.entity.Code;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Stateless
public class CodeServiceEJB implements CodeService {

    @PersistenceContext(name = "dcm4chee-arc", unitName="dcm4chee-arc")
    private EntityManager em;

    @Override
    public Code findOrCreate(org.dcm4che3.data.Code code) {
        if (code == null)
            return null;
        try {
            return find(code);
        } catch (NoResultException e) {
            if (!(code instanceof Code))
                code = new Code(code);
            em.persist(code);
            return (Code) code;
        }
    }

    private Code find(org.dcm4che3.data.Code code) {
        String codingSchemeVersion = code.getCodingSchemeVersion();
        TypedQuery<Code> query = em.createNamedQuery(
                codingSchemeVersion == null
                        ? Code.FIND_BY_CODE_VALUE_WITHOUT_SCHEME_VERSION
                        : Code.FIND_BY_CODE_VALUE_WITH_SCHEME_VERSION,
                    Code.class)
                .setParameter(1, code.getCodeValue())
                .setParameter(2, code.getCodingSchemeDesignator());
        if (codingSchemeVersion != null)
            query.setParameter(3, codingSchemeVersion);
        return query.getSingleResult();
    }

}
