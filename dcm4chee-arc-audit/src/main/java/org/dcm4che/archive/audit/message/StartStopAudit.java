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
 * Portions created by the Initial Developer are Copyright (C) 2011
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

package org.dcm4che.archive.audit.message;

import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.AuditMessages.EventActionCode;
import org.dcm4che3.audit.AuditMessages.EventID;
import org.dcm4che3.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che3.audit.AuditMessages.EventTypeCode;
import org.dcm4che3.audit.AuditMessages.RoleIDCode;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.archive.dto.Participant;

/**
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 * 
 */
public class StartStopAudit extends AuditMessage {

    private boolean start;
    private AuditLogger logger;
    private Participant source;

    /**
     */
    public StartStopAudit(boolean start, AuditLogger logger, Participant source) {
        super();
        this.start = start;
        this.logger = logger;
        this.source = source;
        init();
    }

    private void init() {

        // Event
        this.setEventIdentification(AuditMessages.createEventIdentification(
                EventID.ApplicationActivity, EventActionCode.Execute, logger
                        .timeStamp(), EventOutcomeIndicator.Success, null,
                start ? EventTypeCode.ApplicationStart
                        : EventTypeCode.ApplicationStop));

        // Active Participant 1: Application started (1)
        this.getActiveParticipant().add(
                logger.createActiveParticipant(false, RoleIDCode.Application));

        // Active Participant 2: Persons and or processes that started the
        // Application
        this.getActiveParticipant().add(
                logger.createActiveParticipant(
                        true, source.getIdentity(), null, null,
                        source.getHost(), RoleIDCode.ApplicationLauncher));
        
        this.setAuditSourceIdentification(logger.createAuditSourceIdentification());
    }
}
