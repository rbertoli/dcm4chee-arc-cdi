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
 * Portions created by the Initial Developer are Copyright (C) 2011-2014
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

package org.dcm4chee.archive.mpps.rejection;

import org.dcm4che3.data.UID;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.StoreAction;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.MPPS;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.mpps.MPPSService;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreSession;
import org.dcm4chee.archive.store.decorators.DelegatingStoreService;
import org.dcm4chee.conf.decorators.DynamicDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

/**
 *
 * Rejects all instances that have an associated MPPS which is discontinued due to an incorrectly selected worklist entry
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Roman K
 */
@DynamicDecorator
public class StoreServiceMPPSDecorator extends DelegatingStoreService {

    private static final Logger LOG =
            LoggerFactory.getLogger(StoreServiceMPPSDecorator.class);

    @Inject MPPSService mppsService;

    @Override
    public Instance findOrCreateInstance(EntityManager em, StoreContext context) throws DicomServiceException {
        Instance inst = getNextDecorator().findOrCreateInstance(em, context);
        if (context.getStoreAction() != StoreAction.IGNORE) {
            StoreSession session = context.getStoreSession();
            MPPS mpps = findMPPS(em, session, inst);
            Code incorrectWorklistEntrySelectedCode = (Code) session.getDevice()
                    .getDeviceExtension(ArchiveDeviceExtension.class)
                    .getIncorrectWorklistEntrySelectedCode();

            if (mpps != null && mpps.discontinuedForReason(incorrectWorklistEntrySelectedCode)) {
                inst.setRejectionNoteCode(mpps.getDiscontinuationReasonCode());
                LOG.info("{}: Reject {} by MPPS Discontinuation Reason - {}",
                        context.getStoreSession(),
                        inst,
                        mpps.getDiscontinuationReasonCode());
            }
        }
        return inst;
    }

    private MPPS findMPPS(EntityManager em, StoreSession session, Instance inst) {
        Series series = inst.getSeries();
        String ppsiuid = series.getPerformedProcedureStepInstanceUID();
        String ppscuid = series.getPerformedProcedureStepClassUID();
        MPPS mpps = null;

        // if series reference MPPS
        if (ppsiuid != null && !UID.ModalityPerformedProcedureStepSOPClass.equals(ppscuid)) {

            // check if there is an applicable MPPS stored in the session, otherwise get from the db
            mpps = (MPPS) session.getProperty(MPPS.class.getName());
            if (mpps == null || !mpps.getSopInstanceUID().equals(ppsiuid)) {
                try {
                    mpps = em.createNamedQuery(MPPS.FIND_BY_SOP_INSTANCE_UID, MPPS.class)
                            .setParameter(1, ppsiuid)
                            .getSingleResult();
                } catch (NoResultException ignored) {
                }
            }
        }

        session.setProperty(MPPS.class.getName(), mpps);
        return mpps;
    }

}
