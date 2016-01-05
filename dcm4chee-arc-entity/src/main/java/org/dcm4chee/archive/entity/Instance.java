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
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.storage.conf.Availability;
import org.hibernate.annotations.OptimisticLock;

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
        name=Instance.FIND_BY_SOP_INSTANCE_UID_EAGER,
        query="SELECT i FROM Instance i "
                + "JOIN FETCH i.series se "
                + "JOIN FETCH se.study st "
                + "JOIN FETCH st.patient p "
                + "JOIN FETCH i.attributesBlob "
                + "JOIN FETCH se.attributesBlob "                
                + "JOIN FETCH st.attributesBlob "                
                + "JOIN FETCH p.attributesBlob "
                + "LEFT JOIN FETCH p.patientName pn "
                + "LEFT JOIN FETCH st.referringPhysicianName rpn "
                + "LEFT JOIN FETCH se.performingPhysicianName ppn "
                + "WHERE i.sopInstanceUID = ?1"),            
@NamedQuery(
        name=Instance.FIND_BY_SOP_INSTANCE_UID_EAGER_MANY,
        query="SELECT i FROM Instance i "
                + "JOIN FETCH i.series se "
                + "JOIN FETCH se.study st "
                + "JOIN FETCH st.patient p "
                + "JOIN FETCH i.attributesBlob "
                + "JOIN FETCH se.attributesBlob "                
                + "JOIN FETCH st.attributesBlob "                
                + "JOIN FETCH p.attributesBlob "
                + "LEFT JOIN FETCH p.patientName pn "
                + "LEFT JOIN FETCH st.referringPhysicianName rpn "
                + "LEFT JOIN FETCH se.performingPhysicianName ppn "
                + "WHERE i.sopInstanceUID IN (:uids)"), 
@NamedQuery(
    name=Instance.FIND_BY_SERIES_INSTANCE_UID,
    query="SELECT i FROM Instance i "
            + "WHERE i.series.seriesInstanceUID = ?1")})
@Entity
@Table(name = "instance")
public class Instance implements Serializable {

    private static final long serialVersionUID = -6510894512195470408L;

    public static final String FIND_BY_SOP_INSTANCE_UID_EAGER =
            "Instance.findBySOPInstanceUID.eager";
    public static final String FIND_BY_SOP_INSTANCE_UID_EAGER_MANY =
            "Instance.findBySOPInstanceUIDMany.eager";
    public static final String FIND_BY_SERIES_INSTANCE_UID =
            "Instance.findBySeriesInstanceUID";

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

    @Column(name = "sop_iuid", updatable = false, unique = true)
    private String sopInstanceUID;

    //@Basic(optional = false)
    @Column(name = "sop_cuid", updatable = false)
    private String sopClassUID;

    //@Basic(optional = false)
    @Column(name = "inst_no")
    private String instanceNumber;

    //@Basic(optional = false)
    @Column(name = "content_datetime", nullable=true)
    private Date contentDateTime;

    //@Basic(optional = false)
    @Column(name = "sr_complete")
    private String completionFlag;

    //@Basic(optional = false)
    @Column(name = "sr_verified")
    private String verificationFlag;

    //@Basic(optional = false)
    @Column(name = "inst_custom1")
    private String instanceCustomAttribute1;

    //@Basic(optional = false)
    @Column(name = "inst_custom2")
    private String instanceCustomAttribute2;

    //@Basic(optional = false)
    @Column(name = "inst_custom3")
    private String instanceCustomAttribute3;

    @Column(name = "retrieve_aets")
    private String retrieveAETs;

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    private Collection<ExternalRetrieveLocation> externalRetrieveLocations;

    //@Basic(optional = false)
    @Column(name = "availability")
    private Availability availability;

    @OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "srcode_fk")
    private Code conceptNameCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reject_code_fk")
    private Code rejectionNoteCode;

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    private Collection<VerifyingObserver> verifyingObservers;

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    private Collection<ContentItem> contentItems;

    @ManyToOne(optional = false)
    @JoinColumn(name = "series_fk")
    private Series series;

    @OptimisticLock(excluded = true) // updates of locations should NOT increment the Instance version
    @ManyToMany(mappedBy = "instances")
    private Collection<Location> locations;

    @Transient
    private Attributes cachedAttributes;

    @Override
    public String toString() {
        return "Instance[pk=" + pk
                + ", uid=" + sopInstanceUID
                + ", class=" + sopClassUID
                + ", no=" + instanceNumber
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;

        // update the series, which also has an important side-effect: it will increase the version field of the series
        // (which will in turn also increase the version field of the study).
        // this is necessary to ensure correct calculation of derived fields for series/studies.
        series.setUpdatedTime(now);
    }

    @PreUpdate
    public void onPreUpdate() {
        Date now = new Date();
        updatedTime = now;

        // update the series, which also has an important side-effect: it will increase the version field of the series
        // (which will in turn also increase the version field of the study).
        // this is necessary to ensure correct calculation of derived fields for series/studies.
        series.setUpdatedTime(now);
    }

    @PreRemove
    public void onPreRemove() {
        Date now = new Date();
        // update the series, which also has an important side-effect: it will increase the version field of the series.
        // this is necessary to ensure correct calculation of derived fields for series/studies.
        series.setUpdatedTime(now);
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

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public String getInstanceNumber() {
        return instanceNumber;
    }

    public void setContentDateTime(Date contentDateTime) {
        this.contentDateTime = contentDateTime;
    }

    public Date getContentDateTime() {
        return contentDateTime;
    }

    public String getCompletionFlag() {
        return completionFlag;
    }

    public String getVerificationFlag() {
        return verificationFlag;
    }

    public String getInstanceCustomAttribute1() {
        return instanceCustomAttribute1;
    }

    public String getInstanceCustomAttribute2() {
        return instanceCustomAttribute2;
    }

    public String getInstanceCustomAttribute3() {
        return instanceCustomAttribute3;
    }

    public String[] getRetrieveAETs() {
        return StringUtils.split(retrieveAETs, '\\');
    }

    public String getRawRetrieveAETs() {
        return retrieveAETs;
    }

    public void setRetrieveAETs(String... retrieveAETs) {
        this.retrieveAETs = StringUtils.concat(retrieveAETs, '\\');
    }

    /**
     * Adds a retrieve AET.
     * @param aet AET to add
     * @return Returns {@code true} if the AET was added, returns {@code false} if the AET already
     * existed and therefore was not added.
     */
    public boolean addRetrieveAET(String aet) {
        if(retrieveAETs == null) {
            setRetrieveAETs(aet);
        }
        
        String[] oldRetrieveAETs = getRetrieveAETs();
        for(String retrieveAET : oldRetrieveAETs) {
            if(retrieveAET.equals(aet)) {
                return false;
            }
        }
        
        String[] newRetrieveAETs = new String[oldRetrieveAETs.length + 1];
        System.arraycopy(oldRetrieveAETs, 0, newRetrieveAETs, 0, oldRetrieveAETs.length);
        newRetrieveAETs[oldRetrieveAETs.length] = aet;
        
        setRetrieveAETs(newRetrieveAETs);
        return true;
    }

    public String getEncodedRetrieveAETs() {
        return retrieveAETs;
    }

    public void setEncodedRetrieveAETs(String retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
    }

    public String[] getAllRetrieveAETs() {
        return Utils.decodeAETs(retrieveAETs);
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public Code getConceptNameCode() {
        return conceptNameCode;
    }

    public void setConceptNameCode(Code conceptNameCode) {
        this.conceptNameCode = conceptNameCode;
    }

    public Code getRejectionNoteCode() {
        return rejectionNoteCode;
    }

    public void setRejectionNoteCode(Code rejectionNoteCode) {
        this.rejectionNoteCode = rejectionNoteCode;
    }

    public Collection<VerifyingObserver> getVerifyingObservers() {
        return verifyingObservers;
    }

    public void setVerifyingObservers(
            Collection<VerifyingObserver> verifyingObservers) {
        this.verifyingObservers = verifyingObservers;
    }

    public Collection<ContentItem> getContentItems() {
        return contentItems;
    }

    public void setContentItems(Collection<ContentItem> contentItems) {
        this.contentItems = contentItems;
    }

    /**
     * @return Locations of instance. Note: This collection can NOT be used to update the locations of an instance,
     * as Location is the managing (owning) side of the relationship. Please use {@link Location#addInstance(Instance)}
     * instead.
     */
    public Collection<Location> getLocations() {
        return locations;
    }

    public void setLocations(Collection<Location> locations) {
        this.locations = locations;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }
    
    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Collection<ExternalRetrieveLocation> getExternalRetrieveLocations() {
        return externalRetrieveLocations;
    }

    public void setExternalRetrieveLocations(
            Collection<ExternalRetrieveLocation> externalRetrieveLocations) {
        this.externalRetrieveLocations = externalRetrieveLocations;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr, String nullValue) {
        sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        sopClassUID = attrs.getString(Tag.SOPClassUID);
        instanceNumber = attrs.getString(Tag.InstanceNumber, nullValue);
        Date dt = attrs.getDate(Tag.ContentDateAndTime, new DatePrecision(Calendar.SECOND));
        if (dt != null) {
            Calendar adjustedDateTimeCal = new GregorianCalendar();
            adjustedDateTimeCal.setTime(dt);
            adjustedDateTimeCal.set(Calendar.MILLISECOND, 0);
            contentDateTime = adjustedDateTimeCal.getTime();
        }
        completionFlag = Utils.upper(attrs.getString(Tag.CompletionFlag, nullValue));
        verificationFlag = Utils.upper(attrs.getString(Tag.VerificationFlag, nullValue));

        instanceCustomAttribute1 = 
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), nullValue);
        instanceCustomAttribute2 =
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), nullValue);
        instanceCustomAttribute3 =
                AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), nullValue);

        if (attributesBlob == null)
                attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getCompleteSelection(attrs)));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getCompleteSelection(attrs)));
    }

}
