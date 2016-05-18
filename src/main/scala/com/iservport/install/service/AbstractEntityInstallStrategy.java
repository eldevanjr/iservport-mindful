package com.iservport.install.service;


import org.helianto.core.domain.*;
import org.helianto.core.repository.*;
import org.helianto.install.service.EntityInstallStrategy;
import org.helianto.install.service.IdentityCrypto;
import org.helianto.install.service.UserInstallService;
import org.helianto.security.domain.UserAuthority;
import org.helianto.security.internal.Registration;
import org.helianto.security.repository.UserAuthorityRepository;
import org.helianto.user.domain.User;
import org.helianto.user.domain.UserGroup;
import org.helianto.user.repository.UserGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import javax.inject.Inject;
import java.util.List;

/**
 * Base class to strategies that install important entities when the database is clean.
 * 
 * @author mauriciofernandesdecastro
 */
public abstract class AbstractEntityInstallStrategy
	implements EntityInstallStrategy, InitializingBean
{

	protected static final Logger logger = LoggerFactory.getLogger(AbstractEntityInstallStrategy.class);
	
	protected static final String DEFAULT_CONTEXT_NAME = "DEFAULT";
	
	@Inject
	private OperatorRepository contextRepository;
	
	@Inject
	private CountryInstaller countryInstaller;
	
	@Inject
	private CityInstaller cityInstaller;
	
	@Inject
	private CityRepository cityRepository;
	
	@Inject
	private IdentityRepository identityRepository;
	
	@Inject
	private LeadRepository leadRepository;

	@Inject
	private IdentityCrypto identityCrypto; 
	
	@Inject
	private EntityRepository entityRepository;

	@Inject
	private UserInstallService userInstallService;
	
	@Inject
	private UserGroupRepository userGroupRepository;
	
	@Inject
	private UserAuthorityRepository userAuthorityRepository;
	
    private String contextName;
    
	@Inject
	private Environment env;
	
	/**
	 * Initial secret. First password is generated by this string.
	 */
	protected String getInitialSecret() {
		return "h3l1@nt0";
	};
	
	/**
	 * Method to run once after the first installation.
	 * 
	 * @param context
	 * @param rootEntity
	 * @param rootUser
	 */
	protected void runAfterInstall(Operator context, Entity rootEntity, User rootUser) {
		
	};
	
	/**
	 * Context name.
	 */
	public String getContextName() {
		return contextName;
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		contextName = env.getProperty("helianto.defaultContextName", DEFAULT_CONTEXT_NAME);
//		String contextDataLocation = env.getProperty("helianto.defaultContextName", DEFAULT_CONTEXT_NAME);

		Operator context = contextRepository.findByOperatorName(contextName);
		if (context==null) {
			install();
		}
		
	}
	
	/**
	 * Run once at installation
	 */
	protected final void install() {
		Operator context = contextRepository.saveAndFlush(new Operator(contextName));
		logger.info("Created {}.", context);
		Country country = countryInstaller.installCountries(context);
		City rootCity = cityInstaller.installStatesAndCities(context, country);
		
		String rootEntityAlias = env.getProperty("helianto.rootEntityAlias", DEFAULT_CONTEXT_NAME);
		String rootPrincipal = env.getRequiredProperty("helianto.rootPrincipal");
		String rootFirstName = env.getRequiredProperty("helianto.rootFirstName");
		String rootLastName = env.getRequiredProperty("helianto.rootLastName");
		String rootDisplayName = env.getProperty("helianto.rootDisplayName", rootFirstName);
		String initialSecret = env.getProperty("helianto.initialSecret", getInitialSecret());
		
		// Root identity
		Identity rootIdentity = identityRepository.findByPrincipal(rootPrincipal);
		if(rootIdentity==null){
			rootIdentity= new Identity(rootPrincipal);
			rootIdentity.setDisplayName(rootDisplayName);
			rootIdentity.getPersonalData().setFirstName(rootFirstName);
			rootIdentity.getPersonalData().setLastName(rootLastName);
			rootIdentity = identityRepository.saveAndFlush(rootIdentity);
			logger.info("Created root identity {}.", rootIdentity);	
			identityCrypto.createIdentitySecret(rootIdentity, initialSecret, false);
		}

		// Root entity
		Entity rootEntity = new Entity(context, rootEntityAlias);
		rootEntity.setCity(rootCity);
		rootEntity.setCityId(rootCity.getId());
		rootEntity = installEntity(context, rootEntity);
		
		// Root user
		User rootUser = userInstallService.installUser(rootEntity, rootIdentity.getPrincipal());
		
		// Root authorities
		UserGroup admin = userGroupRepository.findByEntity_IdAndUserKey(rootEntity.getId(), "ADMIN");
		
		// TODO pay attention to the hard coded ADMIN group here; see contextGroups for a better solution
		
		if (admin==null) {
			throw new IllegalArgumentException("Unable to install context, ADMIN group not found; check your contextGroups definition to ensure proper installation.");
		}
		UserAuthority rootAuthority = new UserAuthority(admin, "ADMIN");
		rootAuthority.setServiceExtension("READ,WRITE,MANAGER");
		userAuthorityRepository.saveAndFlush(rootAuthority);
		
		runAfterInstall(context, rootEntity, rootUser);
	}
	
	/**
	 * Basic prototype creation.
	 * 
	 * @param alias
	 * @param summary
	 * @param type
	 * @deprecated
	 */
	protected Entity createPrototype(String alias, String summary, char type) {
		Entity entity = new Entity();
		entity.setAlias(alias);
		entity.setSummary(summary);
		entity.setEntityType(type);
		return entity;
	}
	
	/**
	 * Basic prototype creation.
	 * 
	 * @param newAlias
	 * @param form
	 */
	protected Entity createPrototype(String newAlias, Registration form) {
		Entity entity = new Entity();
		entity.setCityId(form.getCityId());
		entity.setAlias(newAlias);
//		entity.setSummary(form.getSummary());
//		entity.setEntityType(form.getEntityType());
//		entity.setExternalLogoUrl(form.getExternalLogoUrl());
		entity.setEntityDomain(form.getEntityAlias());
		return entity;
	}
	
	public Entity installEntity(Operator context, Entity prototype) {
		if (context==null) {
			throw new IllegalArgumentException("Unable to find context");
		}
		Entity entity = entityRepository.findByContextNameAndAlias(context.getOperatorName(), prototype.getAlias());
		if (entity==null) {
			logger.info("Will install entity for context {} and alias {}.", context.getOperatorName(), prototype.getAlias());
			if (prototype==null || prototype.getCityId()==0) {
				throw new IllegalArgumentException("A city is required to build an entity.");
			}
			if (cityRepository==null) {System.out.println("AHHHH");}
			City city = cityRepository.findOne(prototype.getCityId());
			if (city==null) {
				logger.error("Unable to create entity, city with id {} not found", prototype.getCityId());
				throw new IllegalArgumentException("Unable to create entity");
			}
			prototype.setCity(city);
			entity = entityRepository.saveAndFlush(new Entity(context, prototype));
		}
		else {
			logger.debug("Found existing entity for context {} and alias {}.", context.getOperatorName(), prototype.getAlias());
		}
		return entity;
	}
	
	/**
	 * Create entities.
	 * 
	 * @param prototypes
	 * @param identity
	 */
	public void createEntities(Operator context, List<Entity> prototypes, Identity identity) {
		Entity entity = null;
		for (Entity prototype: prototypes) {
			entity = installEntity(context, prototype);
			if(entity!=null){
				createUser(entity, identity);
			}
		}
	}
	/**
	 * Create new user.
	 * 
	 * @param entity
	 * @param identity
     */
	public User createUser(Entity entity, Identity identity) {
		try {
			String principal = identity.getPrincipal();
			User user = userInstallService.installUser(entity,  principal);
			removeLead(principal);
			return user;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Remove temporary lead.
	 * 
	 * @param leadPrincipal
	 */
	public final String removeLead(String leadPrincipal){
		List<Lead> leads = leadRepository.findByPrincipal(leadPrincipal);	
		for (Lead lead : leads) {
			leadRepository.delete(lead);
		}
		return leadPrincipal;
	}

}
