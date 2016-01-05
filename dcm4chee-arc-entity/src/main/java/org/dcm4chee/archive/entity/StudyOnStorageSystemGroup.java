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

package org.dcm4chee.archive.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
@NamedQueries({
@NamedQuery(
        name=StudyOnStorageSystemGroup.FIND_STUDIES_NO_STG_GROUP,
        query="SELECT s FROM Study s WHERE NOT EXISTS "
                + "(SELECT stg from StudyOnStorageSystemGroup stg "
                + "WHERE stg.study = s)"),
@NamedQuery(
    name=StudyOnStorageSystemGroup.FIND_BY_STUDY_INSTANCE_UID_AND_GRP_UID,
    query="SELECT s FROM StudyOnStorageSystemGroup s "
            + "WHERE s.study.studyInstanceUID = ?1 "
            + "and s.storageSystemGroupID = ?2"),
    @NamedQuery(
        name=StudyOnStorageSystemGroup.FIND_BY_STUDY_INSTANCE_UID_AND_GRP_UID_MARKED,
        query = "SELECT s from StudyOnStorageSystemGroup s "
                + "where s.study.studyInstanceUID = ?1 "
                + "and s.storageSystemGroupID = ?2 "
                + "and s.markedForDeletion = TRUE"
            )
})
@Entity
@Table(name = "study_on_stg_sys", uniqueConstraints = 
	@UniqueConstraint(columnNames={"study_fk","storage_system_group_id"}))
public class StudyOnStorageSystemGroup implements Serializable {

    private static final long serialVersionUID = -370822446832524107L;

    public static final String FIND_BY_STUDY_INSTANCE_UID_AND_GRP_UID = 
            "StudyOnStorageSystemGroup.findByStudyInstanceUIDAndGrpUID";

    public static final String FIND_STUDIES_NO_STG_GROUP =
            "StudyOnStorageSystemGroup.findStudiesNoStgGroup";

    public static final String FIND_BY_STUDY_INSTANCE_UID_AND_GRP_UID_MARKED =
            "StudyOnStorageSystemGroup.findByStudyInstanceUIDAndGrpUIDMarked";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Column(name = "access_time")
    @Basic(optional = false)
    private Date accessTime;

    @ManyToOne(optional = false)
    @JoinColumn(name = "study_fk")
    private Study study;

    @Column(name = "marked_for_deletion")
    @Basic(optional = false)
    private boolean markedForDeletion;

    @Column(name = "storage_system_group_id")
    @Basic(optional = false)
    private String storageSystemGroupID;

    public Date getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(Date accessTime) {
        this.accessTime = accessTime;
    }

    public Study getStudy() {
        return study;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public boolean isMarkedForDeletion() {
        return markedForDeletion;
    }

    public void setMarkedForDeletion(boolean markedForDeletion) {
        this.markedForDeletion = markedForDeletion;
    }

    public String getStorageSystemGroupID() {
        return storageSystemGroupID;
    }

    public void setStorageSystemGroupID(String storageSystemGroupID) {
        this.storageSystemGroupID = storageSystemGroupID;
    }

    public long getPk() {
        return pk;
    }
}
