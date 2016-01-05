package org.dcm4chee.archive.store.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.StoreAction;
import org.dcm4chee.archive.conf.StoreParam;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Location;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.StoreSession;

/**
 * 
 * @author Roberto Bertolino <roberto.bertolino@gmail.com>
 *
 */

// The @Stateless annotation eliminates the need for manual transaction
// demarcation

@Stateless
public class StatelessEM {

	@Inject
	private EntityManager em;

	@Inject
	private Logger log;

	public void register(Patient patient) throws Exception {
		log.info("Registering: " + patient);
		em.persist(patient);
	}

	public void register(Instance instance) throws Exception {
		log.info("Registering: " + instance);
		em.persist(instance);
	}

	public void register(Series series) throws Exception {
		log.info("Registering: " + series);
		em.persist(series);
	}

	public void register(Study study) throws Exception {
		log.info("Registering: " + study);
		em.persist(study);
	}

	public void register(Location location) throws Exception {
		log.info("Registering: " + location);
		em.persist(location);
	}

	public Instance findOrCreateInstance(StoreContext context) throws DicomServiceException {
		StoreSession session = context.getStoreSession();
		StoreParam storeParam = session.getStoreParam();
		StoreService service = session.getStoreService();
		Collection<Location> replaced = new ArrayList<Location>();

		try {

			Attributes attrs = context.getAttributes();
			Instance inst = em.createNamedQuery(Instance.FIND_BY_SOP_INSTANCE_UID_EAGER, Instance.class)
					.setParameter(1, attrs.getString(Tag.SOPInstanceUID)).getSingleResult();
			StoreAction action = service.instanceExists(em, context, inst);
			log.info("{}: {} already exists - {}");
			context.setStoreAction(action);
			switch (action) {
			case RESTORE:
			case UPDATEDB:
				service.updateInstance(em, context, inst);
			case IGNORE:
				return inst;
			case REPLACE:
				for (Iterator<Location> iter = inst.getLocations().iterator(); iter.hasNext();) {
					Location fileRef = iter.next();
					// no other instances referenced through alias table
					if (fileRef.getInstances().size() == 1) {
						// delete
						replaced.add(fileRef);
					} else {
						// remove inst
						fileRef.getInstances().remove(inst);
					}
					iter.remove();
				}
				em.remove(inst);
			}
		} catch (NoResultException e) {
			context.setStoreAction(StoreAction.STORE);
		} catch (DicomServiceException e) {
			throw e;
		} catch (Exception e) {
			throw new DicomServiceException(Status.UnableToProcess, e);
		}

		Instance newInst = service.createInstance(em, context);

		// delete replaced
		try {
			//TODO reactivate
			// locationManager.scheduleDelete(replaced, 0);
		} catch (Exception e) {
			log.info("StoreService : Error deleting replaced location - {}");
		}
		return newInst;
	}
}
