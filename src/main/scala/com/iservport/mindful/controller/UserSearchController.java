package com.iservport.mindful.controller;

import java.util.List;

import javax.inject.Inject;

import org.helianto.security.internal.UserAuthentication;
import org.helianto.user.repository.UserReadAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.iservport.mindful.internal.QualifierAdapter;
import com.iservport.mindful.repository.UserRoleReadAdapter;
import com.iservport.mindful.service.UserCommandService;
import com.iservport.mindful.service.UserQueryService;

/**
 * Controlador de usuários (eleitores).
 * 
 * @author mauriciofernandesdecastro
 */
@RequestMapping(value={"/app/user"})
@Controller
public class UserSearchController {

	private static final Logger logger = LoggerFactory.getLogger(ParlamentoSearchController.class);
	
	@Inject
	private UserQueryService userQueryService;
	
	@Inject
	private UserCommandService userCommandService;
	
	/**
	 * Home.
	 * 
	 * GET		/app/user/
	 */
	@PreAuthorize("hasAuthority('ROLE_USER')")
	@RequestMapping(value={"/", ""}, method=RequestMethod.GET)
	public String home(UserAuthentication userAuthentication, Model model) {
		String base = "user";
		logger.info("User id: {} loading {} page.", userAuthentication.getUserId(), base);
		model.addAttribute("baseName", base);
		model.addAttribute("layoutSize", "2");
		return "frame-angular";
	}
	
	/**
	 * Lista papéis de usuário.
	 *
	 * GET		/app/user/role?userId
	 */
	@PreAuthorize("isAuthenticated()")
	@RequestMapping(value={"/role"}, method=RequestMethod.GET)
	@ResponseBody
	public Page<UserRoleReadAdapter> userRole(UserAuthentication userAuthentication
			, @RequestParam(required = false) Integer userId
			, @RequestParam(required = false, defaultValue = "0") Integer pageNumber) {
		if (userId==null) {
			return userQueryService.userRole(userAuthentication.getUserId(), 0);
		}
		return userQueryService.userRole(userId, pageNumber);
	}

	/**
	 * Lista qualificadores.
	 *
	 * GET /app/user/qualifier
	 */
	@PreAuthorize("hasAuthority('ROLE_USER')")
	@RequestMapping(value = { "/qualifier" }, method = RequestMethod.GET)
	@ResponseBody
	public List<QualifierAdapter> qualifierList(UserAuthentication userAuthentication) {
		return userQueryService.qualifierList(userAuthentication.getEntityId());
	}
	
	/**
	 * Lista usuários.
	 *
	 * GET /app/user/?userGroupId
	 */
	@PreAuthorize("hasAuthority('ROLE_USER')")
	@RequestMapping(value = { "/", "" }, method = RequestMethod.GET, params="{userGroupId}")
	@ResponseBody
	public Page<UserReadAdapter> userList(UserAuthentication userAuthentication, Integer userGroupId) {
		return userQueryService.userList(userGroupId, "A", 0); // TODO page users
	}
	
//	/**
//	 * Enumeração interna para definir natureza
//	 * de grupos de usuários.
//	 * 
//	 * @author mauriciofernandesdecastro
//	 */
//	public enum InternalUserNature implements KeyNameAdapter {
//		
//		ELEITORES('E'),
//		PARLAMENTARES('P'),
//		ADMIN('A');
//		
//		private char value;
//		
//		/**
//		 * Construtor.
//		 * 
//		 * @param value
//		 */
//		private InternalUserNature(char value) {
//			this.value = value;
//		}
//		
//		public Serializable getKey() {
//			return this.value;
//		}
//		
//		@Override
//		public String getCode() {
//			return value+"";
//		}
//		
//		@Override
//		public String getName() {
//			return name();
//		}
//		
//	}
	
}
