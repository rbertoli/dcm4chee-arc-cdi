/*
 * *** BEGIN LICENSE BLOCK *****
 *  Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  Agfa Healthcare.
 *  Portions created by the Initial Developer are Copyright (C) 2015
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 *  ***** END LICENSE BLOCK *****
 */

package org.dcm4chee.archive.store.session;

import org.dcm4chee.archive.conf.StoreAction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A deferred event that denotes a coarse grained update,
 * i.e. after a numbers of instances has been stored in a single 'session' (possibly over multiple concurrent associations)
 *
 * @author Roman K
 */
public class StudyUpdatedEvent implements Serializable{

    private static final long serialVersionUID = -8854586422835724408L;

    private String studyInstanceUID;

    /**
     * All local AETs that were used to store instances contained in this study update session
     */
    private Set<String> localAETs = new HashSet<>();
    private String sourceAET;
    private List<StoredInstance> storedInstances = new ArrayList<>();
    private Set<String> affectedSeriesUIDs = new HashSet<>();

    public StudyUpdatedEvent() {
    }

    public StudyUpdatedEvent(String studyInstanceUID, String sourceAET) {
        this.studyInstanceUID = studyInstanceUID;
        this.sourceAET = sourceAET;
    }

    public Set<String> getLocalAETs() {
        return localAETs;
    }

    public void setLocalAETs(Set<String> localAETs) {
        this.localAETs = localAETs;
    }

    public String getSourceAET() {
        return sourceAET;
    }

    public void setSourceAET(String sourceAET) {
        this.sourceAET = sourceAET;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(String studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    public List<StoredInstance> getStoredInstances() {
        return storedInstances;
    }

    public void setStoredInstances(List<StoredInstance> storedInstances) {
        this.storedInstances = storedInstances;
    }

    public Set<String> getAffectedSeriesUIDs() {
        return affectedSeriesUIDs;
    }

    public void setAffectedSeriesUIDs(Set<String> affectedSeriesUIDs) {
        this.affectedSeriesUIDs = affectedSeriesUIDs;
    }

    public void addStoredInstance(String localAET, String sopInstanceUID, String seriesInstanceUID, StoreAction storeAction) {
        getStoredInstances().add(new StoredInstance(sopInstanceUID, storeAction, localAET));
        getAffectedSeriesUIDs().add(seriesInstanceUID);
        getLocalAETs().add(localAET);
    }


    public static class StoredInstance implements Serializable {

        private static final long serialVersionUID = 6011313460569751657L;

        public StoredInstance(String sopInstanceUID, StoreAction action, String localAET) {
            this.localAET = localAET;
            this.setSopInstanceUID(sopInstanceUID);
            this.setAction(action);
        }

        private String sopInstanceUID;
        private StoreAction action;
        private String localAET;

        public String getSopInstanceUID() {
            return sopInstanceUID;
        }

        public void setSopInstanceUID(String sopInstanceUID) {
            this.sopInstanceUID = sopInstanceUID;
        }

        public StoreAction getAction() {
            return action;
        }

        public void setAction(StoreAction action) {
            this.action = action;
        }

        public String getLocalAET() {
            return localAET;
        }

        public void setLocalAET(String localAET) {
            this.localAET = localAET;
        }
    }
}
