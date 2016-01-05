package org.dcm4chee.conf.decorators;

import org.dcm4chee.archive.mpps.MPPSService;
import org.dcm4chee.archive.query.DerivedSeriesFields;
import org.dcm4chee.archive.query.DerivedStudyFields;
import org.dcm4chee.archive.query.QueryService;
import org.dcm4chee.archive.retrieve.RetrieveService;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.scu.CStoreSCUService;
import org.dcm4chee.storage.service.StorageService;
import org.dcm4chee.storage.archiver.service.ArchiverService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import java.util.Collection;

@ApplicationScoped
public class DynamicDecoratorProducer {
	
	@Inject 
	@DynamicDecorator
	Instance<DelegatingServiceImpl<StoreService>> dynamicStoreDecorators;
	
	@Inject
	@DynamicDecorator
	Instance<DelegatingServiceImpl<MPPSService>> dynamicMPPSDecorators;
	
	@Inject
	@DynamicDecorator
	Instance<DelegatingServiceImpl<QueryService>> dynamicQueryDecorators;
	
	@Inject
	@DynamicDecorator
	Instance<DelegatingServiceImpl<RetrieveService>> dynamicRetrieveDecorators;
	
	@Inject
	@DynamicDecorator
	Instance<DelegatingServiceImpl<CStoreSCUService>> dynamicCStoreSCUDecorators;

    @Inject
    @DynamicDecorator
    Instance<DelegatingServiceImpl<StorageService>> dynamicStorageDecorators;

    @Inject
    @DynamicDecorator
    Instance<DelegatingServiceImpl<org.dcm4chee.storage.service.RetrieveService>> dynamicStorageRetrieveDecorators;

    @Inject
    @DynamicDecorator
    Instance<DelegatingServiceImpl<ArchiverService>> dynamicArchiverDecorators;

	@Inject
	private DynamicDecoratorManager decoratorManager;
	

	@Produces
	@ConfiguredDynamicDecorators
	public Collection<DelegatingServiceImpl<StoreService>> getConfiguredStoreServiceDynamicDecorators() {
		return decoratorManager.getOrderedDecorators(dynamicStoreDecorators, StoreService.class);
	}
	
	@Produces
	@ConfiguredDynamicDecorators
	public Collection<DelegatingServiceImpl<MPPSService>> getConfiguredMPPSServiceDynamicDecorators() {
		return decoratorManager.getOrderedDecorators(dynamicMPPSDecorators, MPPSService.class);
	}
	
	@Produces
	@ConfiguredDynamicDecorators
	public Collection<DelegatingServiceImpl<QueryService>> getConfiguredQueryServiceDynamicDecorators() {
		return decoratorManager.getOrderedDecorators(dynamicQueryDecorators, QueryService.class);
	}
	
	@Produces
	@ConfiguredDynamicDecorators
	public Collection<DelegatingServiceImpl<RetrieveService>> getConfiguredRetrieveServiceDynamicDecorators() {
		return decoratorManager.getOrderedDecorators(dynamicRetrieveDecorators, RetrieveService.class);
	}
	
	@Produces
	@ConfiguredDynamicDecorators
	public Collection<DelegatingServiceImpl<CStoreSCUService>> getConfiguredCStoreSCUServiceDynamicDecorators() {
		return decoratorManager.getOrderedDecorators(dynamicCStoreSCUDecorators, CStoreSCUService.class);
	}

    @Produces
    @ConfiguredDynamicDecorators
    public Collection<DelegatingServiceImpl<StorageService>> getConfiguredStorageServiceDecorators() {
        return decoratorManager.getOrderedDecorators(dynamicStorageDecorators,
                StorageService.class);
    }

    @Produces
    @ConfiguredDynamicDecorators
    public Collection<DelegatingServiceImpl<org.dcm4chee.storage.service.RetrieveService>> getConfiguredStorageRetrieveServiceDecorators() {
        return decoratorManager.getOrderedDecorators(dynamicStorageRetrieveDecorators,
                org.dcm4chee.storage.service.RetrieveService.class);
    }

    @Produces
    @ConfiguredDynamicDecorators
    public Collection<DelegatingServiceImpl<ArchiverService>> getConfiguredArchiverServiceDecorators() {
        return decoratorManager.getOrderedDecorators(dynamicArchiverDecorators,
                ArchiverService.class);
    }

}
