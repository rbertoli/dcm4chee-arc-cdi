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
package org.dcm4chee.archive.qc.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.QueryParam;
import org.dcm4chee.archive.conf.QueryRetrieveView;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.history.InstanceHistory;
import org.dcm4chee.archive.entity.history.UpdateHistory;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.qc.QCOperationContext;
import org.dcm4chee.archive.qc.QCRetrieveBean;
import org.dcm4chee.archive.qc.QC_OPERATION;
import org.dcm4chee.archive.query.QueryService;
import org.dcm4chee.archive.sc.STRUCTURAL_CHANGE;
import org.dcm4chee.archive.sc.StructuralChangeContainer;
import org.dcm4chee.archive.sc.StructuralChangeContext;
import org.dcm4chee.archive.store.scu.CStoreSCUContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class QCRetrieveBeanImpl.
 * Implementes QBRetrieveBean
 * 
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
@Stateless
public class QCRetrieveBeanImpl implements QCRetrieveBean{

    private static Logger LOG = LoggerFactory.getLogger(QCRetrieveBeanImpl.class);

    private static final String CACHED_UIDS = "qc_cached_uids";
    private static final String CACHED_HISTORY_OBJECTS="qc_cached_history";
    private static final String NUM_QC_REF_QUERIES="number_of_reference_queries_for_qc";
    
    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @PersistenceContext(name="dcm4chee-arc", unitName="dcm4chee-arc")
    EntityManager em;

    private String qcSource="Quality Control";

    @Override
    public boolean requiresReferenceUpdate(String studyInstanceUID, Patient pat) {
        
        if(studyInstanceUID != null) {
            Query query = em.createNamedQuery(InstanceHistory.STUDY_EXISTS_IN_QC_HISTORY_AS_OLD_OR_NEXT);
            query.setParameter(1, studyInstanceUID);
            query.setMaxResults(1);
            return query.getResultList().isEmpty()? false: true;
        }
        
        if(pat == null) {
            LOG.error("{}:  QC info[requiresReferenceUpdate] Failure - "
                    + "Attributes supplied missing PatientID",qcSource);
            throw new IllegalArgumentException("Attributes supplied missing PatientID");
            }
        Query queryStudy = em.createQuery("SELECT s.studyInstanceUID FROM Study s WHERE s.patient = ?1");
        queryStudy.setParameter(1, pat);
        List<String> uids = queryStudy.getResultList();
        Query query = em.createNamedQuery(InstanceHistory.STUDIES_EXISTS_IN_QC_HISTORY_AS_OLD_OR_NEXT);
        query.setParameter("uids", uids);
        query.setMaxResults(1);
        return query.getResultList().isEmpty()? false : true;
    }

    @Override
    public void scanForReferencedStudyUIDs(Attributes attrs, Collection<String> initialColl) {
        if(attrs.contains(Tag.StudyInstanceUID)) {
            initialColl.add(attrs.getString(Tag.StudyInstanceUID));
        }
        for(int i : attrs.tags()) {
            if(attrs.getVR(i) == VR.SQ) {
                for(Attributes item : attrs.getSequence(i))
                scanForReferencedStudyUIDs(item, initialColl);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<InstanceHistory> getReferencedHistory(CStoreSCUContext ctx,
            Collection<String> referencedStudyInstanceUIDs) {
        //check if cache has UIDs and filter the referencedStudyInstanceUIDs list to remove the cached ones
        Collection<String> cachedUIDs = (Collection<String>) ctx.getProperty(CACHED_UIDS);
        Collection<InstanceHistory> cachedHistory = (Collection<InstanceHistory>) ctx.getProperty(CACHED_HISTORY_OBJECTS);
        Collection<String> diff = getUIDsMissingFromCache(referencedStudyInstanceUIDs, cachedUIDs);
        if(diff.size() > 0) {
            Collection<InstanceHistory> resultList = new ArrayList<InstanceHistory>();
            Query query = em.createNamedQuery(InstanceHistory.FIND_DISTINCT_INSTANCES_WHERE_STUDY_OLD_OR_CURRENT_IN_LIST);
            query.setParameter("uids", diff);
            for(Iterator<Object[]> iter =  query.getResultList().iterator();iter.hasNext();) {
                Object[] row = iter.next();
                resultList.add((InstanceHistory) row[0]);
            }
        
            if(ctx.getProperty(NUM_QC_REF_QUERIES)==null) {
                ctx.setProperty(NUM_QC_REF_QUERIES,1);
            } else {
                ctx.setProperty(NUM_QC_REF_QUERIES, ((int)ctx.getProperty(NUM_QC_REF_QUERIES)+1));
            }
            cachedHistory = complementCache(ctx, cachedUIDs, cachedHistory, resultList, diff);
        }
        return cachedHistory;
    }

    private Collection<InstanceHistory> complementCache(CStoreSCUContext ctx, Collection<String> cachedUIDs,
            Collection<InstanceHistory> cachedHistory, Collection<InstanceHistory> resultList, Collection<String> diff) {
        if(diff.isEmpty())
            return cachedHistory;
        else {
            if(cachedUIDs==null)
                cachedUIDs = new ArrayList<>();
            if(cachedHistory==null)
                cachedHistory = new ArrayList<>();
            for(InstanceHistory qci : resultList) {
                if(!cachedHistory.contains(qci)) {
                    cachedHistory.add(qci);
                }
            }
            for(String uid : diff) {
                if(!cachedUIDs.contains(uid)) {
                    cachedUIDs.add(uid);
                }
            }
        }
        ctx.setProperty(CACHED_UIDS, cachedUIDs);
        ctx.setProperty(CACHED_HISTORY_OBJECTS, cachedHistory);
        return cachedHistory;
    }

    private Collection<String> getUIDsMissingFromCache(
            Collection<String> referencedStudyInstanceUIDs,
            Collection<String> cachedUIDS) {
        
        if(cachedUIDS==null)
            return referencedStudyInstanceUIDs;
        ArrayList<String> diff = new ArrayList<String>(referencedStudyInstanceUIDs.size());
        String uid;
        for(Iterator<String> iter = referencedStudyInstanceUIDs.iterator();iter.hasNext();) {
            uid = iter.next();
            diff.add(uid);
        }
        return diff;
    }

    @Override
    public void recalculateQueryAttributes(StructuralChangeContainer changeContainer) {
        LOG.info("Received SC change container , initiating derived fields calculation");
        ArchiveDeviceExtension arcDevExt = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String defaultAETitle;
        try {
            defaultAETitle = arcDevExt.getDefaultAETitle();
        } catch (Exception e) {
            LOG.error("Undefined defaultAETitle, "
                    + "cannot calculate derived fields on MPPS COMPLETE");
            return;
        }
        ApplicationEntity archiveAE = device.getApplicationEntity(defaultAETitle);
        ArchiveAEExtension arcAEExt = archiveAE.getAEExtension(ArchiveAEExtension.class);
        QueryRetrieveView view = arcDevExt.getQueryRetrieveView(arcAEExt.getQueryRetrieveViewID());
        QueryParam param = new QueryParam();
        param.setQueryRetrieveView(view);
        
        for(StructuralChangeContext changeCtx : changeContainer.getContexts()) {
            Enum<?>[] qcUpdateChangeTypes = changeCtx.getSubChangeTypeHierarchy(QC_OPERATION.UPDATE);
            if(qcUpdateChangeTypes != null) {
                QCOperationContext qcCtx = (QCOperationContext)changeCtx;
                UpdateHistory.UpdateScope updateScope = (UpdateHistory.UpdateScope)qcUpdateChangeTypes[1];
                try {
                    if (UpdateHistory.UpdateScope.STUDY.equals(updateScope)) {
                        Study study = findStudyByUID(qcCtx.getUpdateAttributes().getString(Tag.StudyInstanceUID));
                        queryService.createStudyView(study.getPk(), param);
                    }
                    if (UpdateHistory.UpdateScope.SERIES.equals(updateScope)) {
                        Series series = findSeriesByUID(qcCtx.getUpdateAttributes().getString(Tag.SeriesInstanceUID));
                        queryService.createSeriesView(series.getPk(), param);
                    }
                } catch (Exception e) {
                    LOG.error("Error processing updated object event with scope={}"
                            + ", Unable to recalculate query attributes - reason {}", updateScope, e);
                }
            } else {
                Set<String> affectedStudies = changeCtx.getAffectedStudyUIDs();
                try {
                    for (String studyIUID : affectedStudies) {
                        Study study = findStudyByUID(studyIUID);
                        queryService.createStudyView(study.getPk(), param);
                        for (Series series : study.getSeries()) {
                            queryService.createSeriesView(series.getPk(), param);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Study or Series lookup failed, "
                            + "Can not re-calculate derived fields on QC");
                    return;
                }
            }
        }
    }

    private Study findStudyByUID(String studyUID) {
        String queryStr = "SELECT s FROM Study s JOIN FETCH s.series se WHERE s.studyInstanceUID = ?1";
            Query query = em.createQuery(queryStr);
            Study study = null;
            try {
                query.setParameter(1, studyUID);
             study = (Study) query.getSingleResult();
            }
            catch(NoResultException e) {
                LOG.error(
                        "Unable to find study {}, related to"
                        + " an already performed procedure",studyUID);
            }
            return study;
    }

    private Series findSeriesByUID(String seriesUID) {
        String queryStr = "SELECT se FROM Series se WHERE se.seriesInstanceUID = ?1";
            Query query = em.createQuery(queryStr);
            Series series = null;
            try {
                query.setParameter(1, seriesUID);
             series = (Series) query.getSingleResult();
            }
            catch(NoResultException e) {
                LOG.error(
                        "Unable to find serties {}, related to"
                        + " an already performed procedure",seriesUID);
            }
            return series;
    }
}
