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

package org.dcm4chee.archive.entity;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.data.Tag;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.archive.conf.AttributeFilter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
@NamedQuery(
        name=Series.FIND_REJECTED,
        query="SELECT se FROM Series se WHERE se.isRejected = TRUE"),
@NamedQuery(
    name=Series.FIND_BY_SERIES_INSTANCE_UID,
    query="SELECT s FROM Series s WHERE s.seriesInstanceUID = ?1"),
@NamedQuery(
    name=Series.FIND_BY_SERIES_INSTANCE_UID_FETCH_REQ_ATTRS,
    query="SELECT s FROM Series s "
            + "JOIN FETCH s.requestAttributes "
            + "JOIN FETCH s.institutionCode "
            + "WHERE s.seriesInstanceUID = ?1"),
@NamedQuery(
    name=Series.FIND_BY_SERIES_INSTANCE_UID_EAGER,
    query="SELECT se FROM Series se "
            + "JOIN FETCH se.study st "
            + "JOIN FETCH st.patient p "
            + "JOIN FETCH se.attributesBlob "
            + "JOIN FETCH st.attributesBlob "
            + "JOIN FETCH p.attributesBlob "
            + "LEFT JOIN FETCH p.patientName pn "
            + "LEFT JOIN FETCH st.referringPhysicianName rpn "
            + "LEFT JOIN FETCH se.performingPhysicianName ppn "            
            + "WHERE se.seriesInstanceUID = ?1"),
@NamedQuery(
    name=Series.FIND_BY_STUDY_INSTANCE_UID_AND_SOURCE_AET,
    query="SELECT se FROM Series se "
            + "JOIN FETCH se.study st "
            + "JOIN FETCH st.patient p "
            + "JOIN FETCH se.attributesBlob "
            + "JOIN FETCH st.attributesBlob "
            + "JOIN FETCH p.attributesBlob "
            + "WHERE st.studyInstanceUID = ?1 "
            + "AND se.sourceAET = ?2"),
@NamedQuery(
    name=Series.PATIENT_STUDY_SERIES_ATTRIBUTES,
    query="SELECT NEW org.dcm4chee.archive.entity.PatientStudySeriesAttributes("
            + "s.attributesBlob.encodedAttributes, "
            + "s.study.attributesBlob.encodedAttributes, "
            + "s.study.patient.attributesBlob.encodedAttributes) "
            + "FROM Series s WHERE s.pk = ?1")
})
@Entity
@Table(name = "series")
public class Series implements Serializable {

    private static final long serialVersionUID = -8317105475421750944L;

    public static final String FIND_REJECTED = "Series.findRejected";

    public static final String FIND_BY_SERIES_INSTANCE_UID = "Series.findBySeriesInstanceUID";

    public static final String FIND_BY_SERIES_INSTANCE_UID_EAGER = "Series.findBySeriesInstanceUID.eager";

    public static final String PATIENT_STUDY_SERIES_ATTRIBUTES = "Series.patientStudySeriesAttributes";

    public static final String FIND_BY_STUDY_INSTANCE_UID_AND_SOURCE_AET = "Series.findByStudyInstanceUIDAndSourceAET";

    public static final String FIND_BY_SERIES_INSTANCE_UID_FETCH_REQ_ATTRS = "Series.findBySeriesInstanceUIDFetchReqAttrs";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    
    @Version
    @Column(name = "version")
    private long version;    

    //@Basic(optional = false)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    //@Basic(optional = false)
    @Column(name = "updated_time")
    private Date updatedTime;

    //@Basic(optional = false)
    @Column(name = "series_iuid", updatable = false, unique = true)
    private String seriesInstanceUID;

    //@Basic(optional = false)
    @Column(name = "series_no")
    private String seriesNumber;

    //@Basic(optional = false)
    @Column(name = "series_desc")
    private String seriesDescription;

    //@Basic(optional = false)
    @Column(name = "modality")
    private String modality;

    //@Basic(optional = false)
    @Column(name = "department")
    private String institutionalDepartmentName;

    //@Basic(optional = false)
    @Column(name = "institution")
    private String institutionName;

    //@Basic(optional = false)
    @Column(name = "station_name")
    private String stationName;

    //@Basic(optional = false)
    @Column(name = "body_part")
    private String bodyPartExamined;

    //@Basic(optional = false)
    @Column(name = "laterality")
    private String laterality;

    //@Basic(optional = false)
    @Column(name = "pps_start", nullable=true)
    private Date performedProcedureStepStartDateTime;
//
//    //@Basic(optional = false)
//    @Column(name = "pps_start_time")
//    private String performedProcedureStepStartTime;

    //@Basic(optional = false)
    @Column(name = "pps_iuid")
    private String performedProcedureStepInstanceUID;

    //@Basic(optional = false)
    @Column(name = "pps_cuid")
    private String performedProcedureStepClassUID;

    //@Basic(optional = false)
    @Column(name = "series_custom1")
    private String seriesCustomAttribute1;

    //@Basic(optional = false)
    @Column(name = "series_custom2")
    private String seriesCustomAttribute2;

    //@Basic(optional = false)
    @Column(name = "series_custom3")
    private String seriesCustomAttribute3;

    @Column(name = "src_aet")
    private String sourceAET;

    @Column(name = "called_aets", nullable=true)
    private String calledAETs;

    //@Basic(optional = false)
    @Column(name = "is_rejected")
    private boolean isRejected;

    @OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @ManyToOne
    @JoinColumn(name = "perf_phys_name_fk")
    private PersonName performingPhysicianName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inst_code_fk")
    private Code institutionCode;

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    private Collection<RequestAttributes> requestAttributes;

//    @ManyToMany
//    @JoinTable(name = "rel_series_sps", 
//        joinColumns = @JoinColumn(name = "series_fk", referencedColumnName = "pk"),
//        inverseJoinColumns = @JoinColumn(name = "sps_fk", referencedColumnName = "pk"))
//    private Collection<ScheduledProcedureStep> scheduledProcedureSteps;


    @ManyToOne(optional = false)
    @JoinColumn(name = "study_fk")
    private Study study;

    @OneToMany(mappedBy = "series", orphanRemoval = true)
    private Collection<Instance> instances;

    @OneToMany(mappedBy = "series", cascade=CascadeType.ALL, orphanRemoval = true)
    private Collection<SeriesQueryAttributes> queryAttributes;

    @Override
    public String toString() {
        return "Series[pk=" + pk
                + ", uid=" + seriesInstanceUID
                + ", no=" + seriesNumber
                + ", mod=" + modality
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;

        // update the study, which also has an important side-effect: it will increase the version field of the study.
        // this is necessary to ensure correct calculation of derived fields for series/studies.
        study.setUpdatedTime(now);
    }

    @PreUpdate
    public void onPreUpdate() {
        Date now = new Date();
        updatedTime = now;

        // update the study, which also has an important side-effect: it will increase the version field of the study.
        // this is necessary to ensure correct calculation of derived fields for series/studies.
        study.setUpdatedTime(now);
    }

    @PreRemove
    public void onPreRemove() {
        Date now = new Date();
        // update the study, which also has an important side-effect: it will increase the version field of the study.
        // this is necessary to ensure correct calculation of derived fields for series/studies.
        study.setUpdatedTime(now);
    }

    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }
    
    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public String getSeriesNumber() {
        return seriesNumber;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public String getModality() {
        return modality;
    }

    public String getInstitutionalDepartmentName() {
        return institutionalDepartmentName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getStationName() {
        return stationName;
    }

    public String getBodyPartExamined() {
        return bodyPartExamined;
    }

    public String getLaterality() {
        return laterality;
    }

    public PersonName getPerformingPhysicianName() {
        return performingPhysicianName;
    }

    public void setPerformingPhysicianName(PersonName performingPhysicianName) {
        this.performingPhysicianName = performingPhysicianName;
    }

    public Date getPerformedProcedureStepStartDateTime() {
        return performedProcedureStepStartDateTime;
    }

    public void setPerformedProcedureStepStartDateTime(Date performedProcedureStepStartDateTime) {
        this.performedProcedureStepStartDateTime = performedProcedureStepStartDateTime;
    }

    public String getPerformedProcedureStepInstanceUID() {
        return performedProcedureStepInstanceUID;
    }

    public String getPerformedProcedureStepClassUID() {
        return performedProcedureStepClassUID;
    }

    public String getSeriesCustomAttribute1() {
        return seriesCustomAttribute1;
    }

    public String getSeriesCustomAttribute2() {
        return seriesCustomAttribute2;
    }

    public String getSeriesCustomAttribute3() {
        return seriesCustomAttribute3;
    }

    public String getSourceAET() {
        return sourceAET;
    }

    public void setSourceAET(String sourceAET) {
        this.sourceAET = sourceAET;
    }

    public String getEncodedCalledAETs() {
        return calledAETs;
    }

    public String[] getCalledAETs() {
        return calledAETs.split("\\\\");
    }

    public void addCalledAET(String calledAET) {
        if(this.calledAETs == null)
        this.calledAETs = calledAET;
        else
            if(!calledAETs.contains(calledAET))
            this.calledAETs+=  "\\" + calledAET;
    }

	public Code getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(Code institutionCode) {
        this.institutionCode = institutionCode;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
    
    public boolean isRejected() {
        return isRejected;
    }

    public void setRejected(boolean isRejected) {
        this.isRejected = isRejected;
    }

    public Collection<RequestAttributes> getRequestAttributes() {
        return requestAttributes;
    }

    public void setRequestAttributes(Collection<RequestAttributes> requestAttributes) {
        this.requestAttributes = requestAttributes;
    }

//    public Collection<ScheduledProcedureStep> getScheduledProcedureSteps() {
//        return scheduledProcedureSteps;
//    }
//
//    public void setScheduledProcedureSteps(
//            Collection<ScheduledProcedureStep> scheduledProcedureSteps) {
//        this.scheduledProcedureSteps = scheduledProcedureSteps;
//    }

    public Study getStudy() {
        return study;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public Collection<Instance> getInstances() {
        return instances;
    }

    public Collection<SeriesQueryAttributes> getQueryAttributes() {
        return queryAttributes;
    }

    public void clearQueryAttributes() {
        if (queryAttributes != null)
            queryAttributes.clear();
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr, String nullValue) {
        seriesInstanceUID = attrs.getString(Tag.SeriesInstanceUID);
        seriesNumber = attrs.getString(Tag.SeriesNumber, nullValue);
        seriesDescription = attrs.getString(Tag.SeriesDescription, nullValue);
        institutionName = attrs.getString(Tag.InstitutionName, nullValue);
        institutionalDepartmentName = attrs.getString(Tag.InstitutionalDepartmentName, nullValue);
        modality = Utils.upper(attrs.getString(Tag.Modality, nullValue));
        stationName = attrs.getString(Tag.StationName, nullValue);
        bodyPartExamined = Utils.upper(attrs.getString(Tag.BodyPartExamined, nullValue));
        laterality = Utils.upper(attrs.getString(Tag.Laterality, nullValue));
        Attributes refPPS = attrs.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence);
        if (refPPS != null) {
            performedProcedureStepInstanceUID = refPPS.getString(Tag.ReferencedSOPInstanceUID, nullValue);
            performedProcedureStepClassUID = refPPS.getString(Tag.ReferencedSOPClassUID, nullValue);
        } else {
            performedProcedureStepInstanceUID = nullValue;
            performedProcedureStepClassUID = nullValue;
        }
        Date dt = attrs.getDate(Tag.PerformedProcedureStepStartDateAndTime, new DatePrecision(Calendar.SECOND));
        if(dt!=null) {
            Calendar adjustedDateTimeCal = new GregorianCalendar();
            adjustedDateTimeCal.setTime(dt);
            adjustedDateTimeCal.set(Calendar.MILLISECOND, 0);
        performedProcedureStepStartDateTime = adjustedDateTimeCal.getTime();
        }
        seriesCustomAttribute1 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), nullValue);
        seriesCustomAttribute2 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), nullValue);
        seriesCustomAttribute3 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), nullValue);

        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getCompleteSelection(attrs)));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getCompleteSelection(attrs)));
        
    }

}
