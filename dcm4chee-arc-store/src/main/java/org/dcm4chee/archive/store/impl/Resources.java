package org.dcm4chee.archive.store.impl;

import java.util.logging.Logger;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
<<<<<<< HEAD
 * This class uses CDI to alias Java EE resources, such as the persistence
 * context, to CDI beans
=======
 * This class uses CDI to alias Java EE resources, such as the persistence context, to CDI beans
>>>>>>> 7068e6bd548b8b6800e216a5d197eaf21593c69c
 * 
 * @author Roberto Bertolino <roberto.bertolino@gmail.com>
 */

public class Resources {
<<<<<<< HEAD

	@Produces
	@PersistenceContext
	private EntityManager em;

	@Produces
	public Logger produceLog(InjectionPoint injectionPoint) {
		return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
	}
}
=======
	
    @Produces
    @PersistenceContext
    private EntityManager em;
	
    @Produces
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }
}
>>>>>>> 7068e6bd548b8b6800e216a5d197eaf21593c69c
