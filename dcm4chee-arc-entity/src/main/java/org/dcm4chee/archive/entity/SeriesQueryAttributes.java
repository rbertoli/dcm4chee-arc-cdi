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

import javax.persistence.*;

import org.dcm4che3.util.StringUtils;
import org.dcm4chee.storage.conf.Availability;

import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@NamedQueries({
        @NamedQuery(
                name=SeriesQueryAttributes.FIND_BY_VIEW_ID_AND_SERIES_FK,
                query="SELECT sqa FROM SeriesQueryAttributes sqa WHERE sqa.viewID = ?1 AND sqa.series.pk = ?2"),
        @NamedQuery(
                name = SeriesQueryAttributes.CLEAN_FOR_SERIES,
                query="DELETE FROM SeriesQueryAttributes queryAttributes "
                        + "WHERE queryAttributes.series.pk = ?1")
})
@Entity
@Table(name = "series_query_attrs",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"series_fk", "view_id"})})
public class SeriesQueryAttributes {


    public static final String FIND_BY_VIEW_ID_AND_SERIES_FK = "SeriesQueryAttributes.findByViewIDAndSeriesFK";
    public static final String CLEAN_FOR_SERIES = "SeriesQueryAttributes.cleanForSeries";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Column(name = "view_id")
    private String viewID;

    @Column(name = "num_instances")
    private int numberOfInstances;

    @Column(name = "retrieve_aets")
    private String retrieveAETs;

    @Column(name = "availability")
    private Availability availability;

    @Column(name = "num_visible_instances")
    private int numberOfVisibleInstances;

    @Column(name = "last_update_time")
    private Date lastUpdateTime;

    @ManyToOne(optional = false)
    @JoinColumn(name = "series_fk")
    private Series series;

    public long getPk() {
        return pk;
    }

    public String getViewID() {
        return viewID;
    }

    public void setViewID(String viewID) {
        this.viewID = viewID;
    }

    public int getNumberOfInstances() {
        return numberOfInstances;
    }

    public void setNumberOfInstances(int numberOfInstances) {
        this.numberOfInstances = numberOfInstances;
    }

    public String getRawRetrieveAETs() {
        return retrieveAETs;
    }

    public String[] getRetrieveAETs() {
        return StringUtils.split(retrieveAETs, '\\');
    }

    public void setRetrieveAETs(String... retrieveAETs) {
        this.retrieveAETs = StringUtils.concat(retrieveAETs, '\\');
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public int getNumberOfVisibleInstances() {
        return numberOfVisibleInstances;
    }

    public void setNumberOfVisibleInstances(int numberOfVisibleInstances) {
        this.numberOfVisibleInstances = numberOfVisibleInstances;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }
}
