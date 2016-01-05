package org.dcm4chee.archive.store.impl;

import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.dcm4chee.archive.entity.Patient;

/**
 * 
 * @author Roberto Bertolino <roberto.bertolino@gmail.com>
 *
 */

// The @Stateless annotation eliminates the need for manual transaction
// demarcation
@Stateless
public class PatientRegistration {

	@Inject
	private EntityManager em;

	@Inject
	private Logger log;

	public void register(Patient patient) throws Exception {
		log.info("Registering: " + patient);
		em.persist(patient);
	}
}
