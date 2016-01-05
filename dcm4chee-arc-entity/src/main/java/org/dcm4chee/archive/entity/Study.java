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
    name="Study.findByStudyInstanceUID",
    query="SELECT s FROM Study s WHERE s.studyInstanceUID = ?1"),
@NamedQuery(
        name="Study.findByStudyInstanceUID.eager",
        query="SELECT st FROM Study st "
                + "JOIN FETCH st.attributesBlob "
                + "JOIN FETCH st.patient "
                + "p JOIN FETCH p.attributesBlob "
                + "LEFT JOIN FETCH p.patientName pn "
                + "LEFT JOIN FETCH st.referringPhysicianName rpn "                
                + "WHERE st.studyInstanceUID = ?1")
})
@Entity
@Table(name = "study")
public class Study implements Serializable {

    private static final long serialVersionUID = -6358525535057418771L;

    public static final String FIND_BY_STUDY_INSTANCE_UID = "Study.findByStudyInstanceUID";

    public static final String FIND_BY_STUDY_INSTANCE_UID_EAGER = "Study.findByStudyInstanceUID.eager";

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
    @Column(name = "study_iuid", updatable = false, unique = true)
    private String studyInstanceUID;

    //@Basic(optional = false)
    @Column(name = "study_id")
    private String studyID;

   @Column(name = "study_datetime", nullable = true)
    private Date studyDateTime;
    
    //@Basic(optional = false)
    @Column(name = "accession_no")
    private String accessionNumber;

    //@Basic(optional = false)
    @Column(name = "study_desc")
    private String studyDescription;

    //@Basic(optional = false)
    @Column(name = "study_custom1")
    private String studyCustomAttribute1;

    //@Basic(optional = false)
    @Column(name = "study_custom2")
    private String studyCustomAttribute2;

    //@Basic(optional = false)
    @Column(name = "study_custom3")
    private String studyCustomAttribute3;

    @Column(name = "access_control_id")
    private String accessControlID;

    //@Basic(optional = false)
    @Column(name = "is_rejected")
    private boolean isRejected;

    @OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @ManyToOne
    @JoinColumn(name = "ref_phys_name_fk")
    private PersonName referringPhysicianName;

    @ManyToOne
    @JoinColumn(name = "accno_issuer_fk")
    private Issuer issuerOfAccessionNumber;

    @ManyToMany
    @JoinTable(name = "rel_study_pcode", 
        joinColumns = @JoinColumn(name = "study_fk", referencedColumnName = "pk"),
        inverseJoinColumns = @JoinColumn(name = "pcode_fk", referencedColumnName = "pk"))
    private Collection<Code> procedureCodes;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_fk")
    private Patient patient;

    @OneToMany(mappedBy = "study", orphanRemoval = true)
    private Collection<Series> series;

    @OneToMany(mappedBy = "study", cascade=CascadeType.ALL, orphanRemoval = true)
    private Collection<StudyQueryAttributes> queryAttributes;

    @Override
    public String toString() {
        return "Study[pk=" + pk
                + ", uid=" + studyInstanceUID
                + ", id=" + studyID
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedTime = new Date();
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

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getStudyID() {
        return studyID;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public Issuer getIssuerOfAccessionNumber() {
        return issuerOfAccessionNumber;
    }

    public void setIssuerOfAccessionNumber(Issuer issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
    }

    public PersonName getReferringPhysicianName() {
        return referringPhysicianName;
    }

    public void setReferringPhysicianName(PersonName referringPhysicianName) {
        this.referringPhysicianName = referringPhysicianName;
    }

    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }
    
    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public String getStudyCustomAttribute1() {
        return studyCustomAttribute1;
    }

    public String getStudyCustomAttribute2() {
        return studyCustomAttribute2;
    }

    public String getStudyCustomAttribute3() {
        return studyCustomAttribute3;
    }

    public String getAccessControlID() {
        return accessControlID;
    }

    public void setAccessControlID(String accessControlID) {
        this.accessControlID = accessControlID;
    }

    public Date getStudyDateTime() {
        return studyDateTime;
    }

    public void setStudyDateTime(Date studyDateTime) {
        this.studyDateTime = studyDateTime;
    }

    public boolean isRejected() {
        return isRejected;
    }

    public void setRejected(boolean isRejected) {
        this.isRejected = isRejected;
    }

    public Collection<Code> getProcedureCodes() {
        return procedureCodes;
    }

    public void setProcedureCodes(Collection<Code> procedureCodes) {
        this.procedureCodes = procedureCodes;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Collection<Series> getSeries() {
        return series;
    }

    public final Collection<StudyQueryAttributes> getQueryAttributes() {
        return queryAttributes;
    }

    public void clearQueryAttributes() {
        if (queryAttributes != null)
            queryAttributes.clear();
    }
    
    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr, String nullValue) {
        studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        studyID = attrs.getString(Tag.StudyID, nullValue);
        studyDescription = attrs.getString(Tag.StudyDescription, nullValue);
        Date dt = attrs.getDate(Tag.StudyDateAndTime,new DatePrecision(Calendar.SECOND));
        if (dt != null) {
            Calendar adjustedDateTimeCal = new GregorianCalendar();
            adjustedDateTimeCal.setTime(dt);
            adjustedDateTimeCal.set(Calendar.MILLISECOND, 0);
            studyDateTime = adjustedDateTimeCal.getTime();
        }
        accessionNumber = attrs.getString(Tag.AccessionNumber, nullValue);
        studyCustomAttribute1 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), nullValue);
        studyCustomAttribute2 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), nullValue);
        studyCustomAttribute3 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), nullValue);

        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getCompleteSelection(attrs)));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getCompleteSelection(attrs)));
    }

}
