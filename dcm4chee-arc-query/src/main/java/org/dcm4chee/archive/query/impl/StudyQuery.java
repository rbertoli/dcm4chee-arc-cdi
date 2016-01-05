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

package org.dcm4chee.archive.query.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.PrivateTag;
import org.dcm4chee.archive.entity.QStudy;
import org.dcm4chee.archive.entity.QStudyQueryAttributes;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.entity.StudyQueryAttributes;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.query.QueryContext;
import org.dcm4chee.archive.query.util.QueryBuilder;
import org.dcm4chee.storage.conf.Availability;
import org.hibernate.ScrollableResults;
import org.hibernate.StatelessSession;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.jpa.hibernate.HibernateQuery;
import com.mysema.query.types.Expression;
import com.mysema.query.types.Predicate;

import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
class StudyQuery extends AbstractQuery<Study> {

    static final Expression<?>[] SELECT = {
        QStudy.study.pk,                                                 // (0)
        QStudyQueryAttributes.studyQueryAttributes.numberOfInstances,    // (1)
        QStudyQueryAttributes.studyQueryAttributes.numberOfSeries,       // (2)
        QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy,    // (3)
        QStudyQueryAttributes.studyQueryAttributes.sopClassesInStudy,    // (4)
        QStudyQueryAttributes.studyQueryAttributes.retrieveAETs,         // (5)
        QStudyQueryAttributes.studyQueryAttributes.availability,         // (6)
        QStudyQueryAttributes.studyQueryAttributes.numberOfVisibleInstances,// (7)
        QStudyQueryAttributes.studyQueryAttributes.lastUpdateTime,       // (8)
        QueryBuilder.studyAttributesBlob.encodedAttributes,              // (9)
        QueryBuilder.patientAttributesBlob.encodedAttributes             // (10)
    };

    public StudyQuery(QueryContext context, StatelessSession session) {
        super(context, session, QStudy.study);
    }

    @Override
    protected Expression<?>[] select() {
        return SELECT;
    }

    @Override
    protected HibernateQuery applyJoins(HibernateQuery query) {
        query = QueryBuilder.applyStudyLevelJoins(query,
                context.getKeys(),
                context.getQueryParam());
        query = QueryBuilder.applyPatientLevelJoins(query,
                context.getKeys(),
                context.getQueryParam());
        return query;
    }

    @Override
    protected Predicate predicate() {
        BooleanBuilder builder = new BooleanBuilder();
        QueryBuilder.addPatientLevelPredicates(builder,
                context.getPatientIDs(),
                context.getKeys(),
                context.getQueryParam());
        QueryBuilder.addStudyLevelPredicates(builder,
                context.getKeys(),
                context.getQueryParam());
        return builder;
    }

    @Override
    public Attributes toAttributes(ScrollableResults results, QueryContext context) {
        Long studyPk = results.getLong(0);
        Integer numberOfInstancesI = results.getInteger(1);
        int numberOfStudyRelatedInstances;
        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        String retrieveAETs;
        Availability availability;
        int numberOfStudyVisibleInstances;
        Date studyLastUpdateTime;
        if (numberOfInstancesI != null) {
            numberOfStudyRelatedInstances = numberOfInstancesI;
            if (numberOfStudyRelatedInstances == 0)
                return null;

            numberOfStudyRelatedSeries = results.getInteger(2);
            modalitiesInStudy = results.getString(3);
            sopClassesInStudy = results.getString(4);
            retrieveAETs = results.getString(5);
            availability = (Availability) results.get(6);
            numberOfStudyVisibleInstances = results.getInteger(7);
            studyLastUpdateTime = results.getDate(8);
        } else {
            StudyQueryAttributes studyView = context.getQueryService()
                    .createStudyView(studyPk,  context.getQueryParam());
            numberOfStudyRelatedInstances = studyView.getNumberOfInstances();
            if (numberOfStudyRelatedInstances == 0)
                return null;

            numberOfStudyRelatedSeries = studyView.getNumberOfSeries();
            modalitiesInStudy = studyView.getRawModalitiesInStudy();
            sopClassesInStudy = studyView.getRawSOPClassesInStudy();
            retrieveAETs = studyView.getRawRetrieveAETs();
            availability = studyView.getAvailability();
            numberOfStudyVisibleInstances = studyView.getNumberOfVisibleInstances();
            studyLastUpdateTime = studyView.getLastUpdateTime();
        }

        byte[] studyByteAttributes = results.getBinary(9);
        byte[] patientByteAttributes = results.getBinary(10);
        Attributes patientAttrs = new Attributes();
        Attributes studyAttrs = new Attributes();
        Utils.decodeAttributes(patientAttrs, patientByteAttributes);
        Utils.decodeAttributes(studyAttrs, studyByteAttributes);
        Attributes attrs = Utils.mergeAndNormalize(patientAttrs, studyAttrs);
        ArchiveDeviceExtension ade = context.getArchiveAEExtension()
                .getApplicationEntity().getDevice().getDeviceExtension
                        (ArchiveDeviceExtension.class);
        Utils.setStudyQueryAttributes(attrs,
                numberOfStudyRelatedSeries,
                numberOfStudyRelatedInstances,
                modalitiesInStudy,
                sopClassesInStudy,
                numberOfStudyVisibleInstances,
                ade.getPrivateDerivedFields().findStudyNumberOfVisibleInstancesTag(),
                studyLastUpdateTime,
                ade.getPrivateDerivedFields().findStudyUpdateTimeTag());
        Utils.setRetrieveAET(attrs, retrieveAETs);
        Utils.setAvailability(attrs, availability);
        return attrs;
    }

}
