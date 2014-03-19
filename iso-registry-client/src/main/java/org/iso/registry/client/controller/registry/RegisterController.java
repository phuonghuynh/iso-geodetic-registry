/**
 * 
 */
package org.iso.registry.client.controller.registry;

import static de.geoinfoffm.registry.core.security.RegistrySecurity.SUBMITTER_ROLE_PREFIX;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.iso.registry.client.ProposalDtoFactory;
import org.iso.registry.client.RegisterItemViewBean;
import org.iso.registry.client.configuration.BasePathRedirectView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import de.geoinfoffm.registry.api.EntityNotFoundException;
import de.geoinfoffm.registry.api.ItemNotFoundException;
import de.geoinfoffm.registry.api.RegisterItemProposalDTO;
import de.geoinfoffm.registry.api.RegisterItemProposalDTO.ProposalType;
import de.geoinfoffm.registry.api.RegisterItemService;
import de.geoinfoffm.registry.api.RegisterService;
import de.geoinfoffm.registry.core.IllegalOperationException;
import de.geoinfoffm.registry.core.ItemClassConfiguration;
import de.geoinfoffm.registry.core.ItemClassRegistry;
import de.geoinfoffm.registry.core.UnauthorizedException;
import de.geoinfoffm.registry.core.model.Addition;
import de.geoinfoffm.registry.core.model.Appeal;
import de.geoinfoffm.registry.core.model.Proposal;
import de.geoinfoffm.registry.core.model.RegistryUserRepository;
import de.geoinfoffm.registry.core.model.SimpleProposal;
import de.geoinfoffm.registry.core.model.Supersession;
import de.geoinfoffm.registry.core.model.iso19135.InvalidProposalException;
import de.geoinfoffm.registry.core.model.iso19135.ProposalManagementInformationRepository;
import de.geoinfoffm.registry.core.model.iso19135.RE_DecisionStatus;
import de.geoinfoffm.registry.core.model.iso19135.RE_ItemClass;
import de.geoinfoffm.registry.core.model.iso19135.RE_ItemStatus;
import de.geoinfoffm.registry.core.model.iso19135.RE_Register;
import de.geoinfoffm.registry.core.model.iso19135.RE_RegisterItem;
import de.geoinfoffm.registry.core.model.iso19135.RE_SubmittingOrganization;
import de.geoinfoffm.registry.core.security.RegistrySecurity;
import de.geoinfoffm.registry.persistence.ItemClassRepository;
import de.geoinfoffm.registry.persistence.ProposalRepository;
import de.geoinfoffm.registry.persistence.RegisterRepository;
import de.geoinfoffm.registry.persistence.SubmittingOrganizationRepository;
import de.geoinfoffm.registry.persistence.SupersessionRepository;
import de.geoinfoffm.registry.persistence.xml.exceptions.XmlSerializationException;

/**
 * Controller for the register item proposals.
 * 
 * @author Florian Esser
 *
 */
@Controller
@RequestMapping("/register")
public class RegisterController
{
	private static final Logger logger = LoggerFactory.getLogger(RegisterController.class);
	
	@Autowired
	private RegistrySecurity security;
	
	@Autowired
	private RegisterItemService itemService;
	
	@Autowired
	private RegisterService registerService;
	
	@Autowired
	private RegisterRepository registerRepository;

	@Autowired
	private ProposalRepository proposalRepository;
	
	@Autowired
	private ProposalManagementInformationRepository pmiRepository;

	@Autowired
	private SupersessionRepository supersessionRepository;

	@Autowired
	private SubmittingOrganizationRepository suborgRepository;
	
	@Autowired
	private ItemClassRepository itemClassRepository;
	
	@Autowired
	private RegistryUserRepository userRepository;
	
	@Autowired
	private ViewResolver viewResolver;
	
	@Autowired
	private ItemClassRegistry itemClassRegistry;
	
//	@InitBinder
//	protected void initBinder(WebDataBinder binder) {
//		binder.setValidator(new ProposalValidator(itemService));
//	}
	
	@RequestMapping(value = "/{register}", method = RequestMethod.GET)
	@Transactional(readOnly = true)
	public String registerOverview(@PathVariable("register") String registerName, final Model model, final RedirectAttributes redirectAttributes) {
		RE_Register register = findRegister(registerName);
		if (register == null) {
			redirectAttributes.addFlashAttribute("registerName", registerName);
			return "registry/register_notfound";
		}
		model.addAttribute("register", register);
		
		List<RegisterItemViewBean> containedItemViewBeans = new ArrayList<RegisterItemViewBean>();
		Set<RE_RegisterItem> containedItems = register.getContainedItems(Arrays.asList(RE_ItemStatus.VALID, RE_ItemStatus.RETIRED, RE_ItemStatus.SUPERSEDED));
		for (RE_RegisterItem containedItem : containedItems) {
			containedItemViewBeans.add(new RegisterItemViewBean(containedItem));
		}
		model.addAttribute("items", containedItemViewBeans);
		
//		RE_SubmittingOrganization suborg = RegistryUserUtils.getUserSponsor(userRepository);
//		RE_SubmittingOrganization suborg = null;
		Collection<Proposal> proposals = proposalRepository.findByGroupIsNull();
		List<RegisterItemViewBean> proposedItemViewBeans = new ArrayList<RegisterItemViewBean>();
		for (Proposal proposal : proposals) {
			if(!security.may(BasePermission.READ, proposal)) {
				continue;
			}

			if (proposal.getStatus().equals(RE_DecisionStatus.FINAL)) continue;
			if (proposal.hasGroup()) continue;
			
			if (proposal.isContainedIn(register)) {
				Appeal appeal = itemService.findAppeal(proposal);
				if (appeal != null) {
					proposedItemViewBeans.add(new RegisterItemViewBean(appeal));
				}
				else {
					proposedItemViewBeans.add(new RegisterItemViewBean(proposal));
				}
			}
		}
		
		model.addAttribute("proposedItems", proposedItemViewBeans);

		if (!model.containsAttribute("viewMode")) {
			model.addAttribute("viewMode", "contents");
		}
		return "registry/register";
	}

	private RE_Register findRegister(String registerName) {
		RE_Register register = registerRepository.findByName(registerName);
		
		if (register == null) {
			try {
				register = registerRepository.findOne(UUID.fromString(registerName));
			}
			catch (IllegalArgumentException e) {
				// Ignore
			}
		}
		return register;
	}

	@RequestMapping(value = "/{register}/xml", method = RequestMethod.GET)
	@Transactional(readOnly = true)
	public ResponseEntity<String> registerOverviewAsXml(@PathVariable("register") String registerName, final Model model) throws XmlSerializationException {
		RE_Register register = findRegister(registerName);
		if (register == null) {
			throw new EntityNotFoundException(String.format("Register %s does not exist", registerName));
		}
		
		StringWriter sw = new StringWriter();
		registerService.toXml(register, sw);
		
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.add("Content-Type", "text/xml; charset=utf-8");
		return new ResponseEntity<String>(sw.toString(), responseHeaders, HttpStatus.OK);
	}

	@RequestMapping(value = "/{register}/proposals", method = RequestMethod.GET)
	@Transactional
	public String registerOverviewProposals(@PathVariable("register") String registerName, final Model model, final RedirectAttributes redirectAttributes) {
		model.addAttribute("viewMode", "proposals");
		return registerOverview(registerName, model, redirectAttributes);
	}


	/**
	 * Displays an empty view and allows for the creation of a new user.
	 * 
	 * @param user The view-backing {@link RegisterItemProposalDTO} object
	 * @param model The view model
	 * @return The name of the view used
	 * @throws UnauthorizedException 
	 */
	@RequestMapping(value = "/{register}/proposal/addition", method = RequestMethod.GET)
	@Transactional
	public String createProposal(@PathVariable("register") String registerName,
								 @RequestParam(value = "itemClass", required = false) String itemClassUuid,
								 @ModelAttribute("proposal") RegisterItemProposalDTO proposal, 
								 final Model model,
								 final RedirectAttributes redirectAttributes) throws UnauthorizedException {

		RE_Register register = findRegister(registerName);
		if (register == null) {
			redirectAttributes.addFlashAttribute("registerName", registerName);
			return "registry/register_notfound";
		}
		
		security.assertHasEntityRelatedRole(SUBMITTER_ROLE_PREFIX, register);

		model.addAttribute("register", register);
		
		Set<RE_ItemClass> itemClasses = register.getContainedItemClasses();
		if (itemClasses.size() == 1) {
			itemClassUuid = itemClasses.toArray(new RE_ItemClass[] {})[0].getUuid().toString();
			model.addAttribute("itemClassUuid", itemClassUuid);
		}
		model.addAttribute("itemClasses", itemClasses);

		ItemClassConfiguration itemClassConfiguration = null;
		if (!StringUtils.isEmpty(itemClassUuid)) {
			RE_ItemClass selectedItemClass = null;
			for (RE_ItemClass itemClass : itemClasses) {
				if (itemClass.getUuid().toString().toLowerCase().equals(itemClassUuid.toLowerCase())) {
					selectedItemClass = itemClass;
					break;
				}
			}
			
			if (selectedItemClass == null) {
				throw new IllegalArgumentException(String.format("Register %s does not contain item class %s", registerName, itemClassUuid)); 
			}
			
			itemClassConfiguration = itemClassRegistry.getConfiguration(selectedItemClass.getName());
			if (itemClassConfiguration != null) {
				model.addAttribute("itemClassConfiguration", itemClassConfiguration);
			}
			
			proposal = ProposalDtoFactory.getInstance().getProposalDto(selectedItemClass);
			if (proposal.getClass().getCanonicalName().equals(RegisterItemProposalDTO.class.getCanonicalName())) {
				model.addAttribute("itemClassNotConfigured", "true");
			}
			
			proposal.setItemClassUuid(UUID.fromString(itemClassUuid));
			model.addAttribute("itemClass", selectedItemClass.getUuid().toString());
		}

		RE_SubmittingOrganization suborg = suborgRepository.findAll().get(0);
		
		proposal.setProposalType(ProposalType.ADDITION);
		proposal.setSponsorUuid(suborg.getUuid());
		proposal.setTargetRegisterUuid(register.getUuid());
		
		model.addAttribute("proposal", proposal);

		String viewName;
		if (itemClassConfiguration != null && !StringUtils.isEmpty(itemClassConfiguration.getCreateTemplate())) {
			viewName = itemClassConfiguration.getCreateTemplate();
		}
		else {
			viewName = "registry/proposal/create_addition";
		}

		return viewName;
	}
	
	@RequestMapping(value = "/{register}/proposal/supersession", method = RequestMethod.GET)
	@Transactional
	public String createSupersessionProposal(WebRequest request,
			@PathVariable("register") String registerName, 
			final Model model,
			final RedirectAttributes redirectAttributes) throws ItemNotFoundException, UnauthorizedException {

		RE_Register register = findRegister(registerName);
		if (register == null) {
			redirectAttributes.addFlashAttribute("registerName", registerName);
			return "registry/register_notfound";
		}
		
		security.assertHasEntityRelatedRole(SUBMITTER_ROLE_PREFIX, register);
		
//		RE_SubmittingOrganization suborg = RegistryUserUtils.getUserSponsor(userRepository);
		RE_SubmittingOrganization suborg = null;
		SupersessionState state = new SupersessionState(register, suborg, itemService);
		request.setAttribute("supersession", state, WebRequest.SCOPE_SESSION);

		model.addAttribute("state", state);

		Set<RE_ItemClass> itemClasses = register.getContainedItemClasses();
		itemClasses.size();
		model.addAttribute("itemClasses", itemClasses);

		return "registry/proposal/create_supersession";
	}

	@RequestMapping(value = "/{register}/proposal/supersession", params={ "addSupersededItems" })
	@Transactional
	public String addSupersededItemsToSupersession(WebRequest request,
			@PathVariable("register") String registerName,
			final Model model, 
			@RequestParam Map<String, String> supersededItems,
			final RedirectAttributes redirectAttributes) throws InvalidProposalException, IllegalOperationException {

		RE_Register register = findRegister(registerName);
		if (register == null) {
			redirectAttributes.addFlashAttribute("registerName", registerName);
			return "registry/register_notfound";
		}

		addSupersededItemsToSupersession(request, model, supersededItems, itemService);

		Set<RE_ItemClass> itemClasses = register.getContainedItemClasses();
		itemClasses.size();
		model.addAttribute("itemClasses", itemClasses);

		return "registry/proposal/create_supersession";
	}

	public static void addSupersededItemsToSupersession(WebRequest request,
			final Model model, Map<String, String> supersededItems, RegisterItemService itemService)
			throws InvalidProposalException {
		
		SupersessionState state = (SupersessionState)request.getAttribute("supersession", WebRequest.SCOPE_SESSION);
		if (state == null) {
			throw new IllegalStateException("State not initialized");
		}
		
		state.removeAllSupersededItems();

		for (String supersededItemUuid : supersededItems.keySet()) {
			if (!supersededItemUuid.startsWith("supersede_")) continue;
			
			RE_RegisterItem supersededItem = itemService.findOne(UUID.fromString(supersededItemUuid.substring(10)));
			if (supersededItem == null) {
				throw new InvalidProposalException(String.format("Superseded item %s does not exist", supersededItemUuid));
			}
			state.addSupersededItem(supersededItem);
		}
		
		if (state.getSupersededItems().isEmpty()) {
			model.addAttribute("noSupersededItems", "true");
		}
		else {
			state.setStep("supersedingItems");
		}

		request.setAttribute("supersession", state, WebRequest.SCOPE_SESSION);
		model.addAttribute("state", state);
	}

	@RequestMapping(value = "/{register}/proposal/supersession", method = RequestMethod.POST, params={ "addNew" })
	@Transactional
	public String addNewItemToProposalPost(WebRequest request,
			@PathVariable("register") String registerName,
			@RequestParam(value = "itemClass", required = false) String itemClassUuid,
			final Model model,
			final RedirectAttributes redirectAttributes) throws UnauthorizedException {
		
		return addNewItemToProposal(request, registerName, itemClassUuid, model, redirectAttributes);
	}

	@RequestMapping(value = "/{register}/proposal/supersession", params={ "addNew" })
	@Transactional
	public String addNewItemToProposal(WebRequest request,
			@PathVariable("register") String registerName,
			@RequestParam(value = "itemClass", required = false) String itemClassUuid,
			final Model model,
			final RedirectAttributes redirectAttributes) throws UnauthorizedException { 
		
		SupersessionState state = (SupersessionState)request.getAttribute("supersession", WebRequest.SCOPE_SESSION);
		if (state == null) {
			throw new IllegalStateException("State not initialized");
		}
		
		RE_Register targetRegister = findRegister(registerName);
		if (targetRegister == null) {
			redirectAttributes.addFlashAttribute("registerName", registerName);
			return "registry/register_notfound";
		}
		model.addAttribute("register", targetRegister);
		
//		RE_SubmittingOrganization suborg = RegistryUserUtils.getUserSponsor(userRepository);
		RE_SubmittingOrganization suborg = null;

		model.addAttribute("isNew", "true");

		RegisterItemProposalDTO newItem = new RegisterItemProposalDTO();
		newItem.setProposalType(ProposalType.ADDITION);		
		newItem.setSponsorUuid(suborg.getUuid());
		newItem.setTargetRegisterUuid(targetRegister.getUuid());

		model.addAttribute("proposal", newItem);
		model.addAttribute("partOfSupersession", "true");
		
		return createProposal(registerName, itemClassUuid, newItem, model, redirectAttributes);
	}

	@RequestMapping(value = "/{register}/proposal/supersession", params={ "save" })
	@Transactional
	public String saveNewItem(WebRequest request, HttpServletRequest servletRequest,
			@PathVariable("register") String registerName,
			@ModelAttribute("proposal") RegisterItemProposalDTO proposal,
			final Model model) {

		proposal = bindAdditionalAttributes(proposal, servletRequest);

		SupersessionState state = (SupersessionState)request.getAttribute("supersession", WebRequest.SCOPE_SESSION);
		if (state == null) {
			throw new IllegalStateException("State not initialized");
		}
		state.addNewSupersedingItem(proposal);
		
		request.setAttribute("supersession", state, WebRequest.SCOPE_SESSION);
		model.addAttribute("state", state);
		
		return "registry/proposal/create_supersession";
	}

	@RequestMapping(value = "/{register}/proposal/supersession", params={ "saveSupersedingItems" })
	@Transactional
	public String saveSupersedingItemsSupersessionProposal(WebRequest request,
			final Model model) {
		
		SupersessionState state = (SupersessionState)request.getAttribute("supersession", WebRequest.SCOPE_SESSION);
		if (state == null) {
			throw new IllegalStateException("State not initialized");
		}
						
		if (state.getNewSupersedingItems().isEmpty()) {
			model.addAttribute("noSupersedingItems", "true");
		}
		else {
			state.setStep("additionalData");
		}

		request.setAttribute("supersession", state, WebRequest.SCOPE_SESSION);
		model.addAttribute("state", state);

		return "registry/proposal/create_supersession";
		
	}
			

	@RequestMapping(value = "/{register}/proposal/supersession", params={ "saveAdditionalData" })
	@Transactional
	public String saveAdditionalDataSupersessionProposal(WebRequest request,
			@PathVariable("register") String registerName, 
			final Model model,
			@RequestParam Map<String, String> additionalData) {

		SupersessionState state = (SupersessionState)request.getAttribute("supersession", WebRequest.SCOPE_SESSION);
		if (state == null) {
			throw new IllegalStateException("State not initialized");
		}
		
		if (additionalData.containsKey("justification")) {
			state.setJustification(additionalData.get("justification"));
		}
		if (additionalData.containsKey("registerManagerNotes")) {
			state.setRegisterManagerNotes(additionalData.get("registerManagerNotes"));
		}
		if (additionalData.containsKey("controlBodyNotes")) {
			state.setControlBodyNotes(additionalData.get("controlBodyNotes"));
		}

		state.setStep("overview");

		request.setAttribute("supersession", state, WebRequest.SCOPE_SESSION);
		model.addAttribute("state", state);
		
		return "registry/proposal/create_supersession";
		
	}

	@RequestMapping(value = "/{register}/proposal/supersession", params={ "submit" })
	@Transactional
	public View submitSupersessionProposal(WebRequest request,
			@PathVariable("register") String registerName,
			final Model model) throws InvalidProposalException, IllegalOperationException {

		SupersessionState state = (SupersessionState)request.getAttribute("supersession", WebRequest.SCOPE_SESSION);
		if (state == null) {
			throw new IllegalStateException("State not initialized");
		}

		Set<RE_RegisterItem> supersededItems = new HashSet<RE_RegisterItem>();
		for (RegisterItemViewBean supersededItemViewBean : state.getSupersededItems()) {
			RE_RegisterItem supersededItem = itemService.findOne(supersededItemViewBean.getUuid());
			if (supersededItem == null) {
				throw new InvalidProposalException(String.format("Superseded item %s does not exist", supersededItemViewBean.getUuid()));
			}
			supersededItems.add(supersededItem);
		}

		request.removeAttribute("supersession", WebRequest.SCOPE_SESSION);
		
		itemService.proposeSupersession(supersededItems, state.getNewSupersedingItems(), 
				state.getJustification(), state.getRegisterManagerNotes(), state.getControlBodyNotes(), state.getSponsor());

		return new BasePathRedirectView("/register/" + registerName + "/proposals");
//		return "redirect:/register/" + registerName + "/proposals";
	}
	
	@RequestMapping(value = "/{register}/proposal/addition", method = RequestMethod.POST)
	public String submitProposal(WebRequest request, ServletRequest servletRequest, @PathVariable("register") String registerName, 
			@Valid @ModelAttribute("proposal") RegisterItemProposalDTO proposal,
			@RequestParam Map<String, String> allParams,
			final BindingResult bindingResult, final Model model, final RedirectAttributes redirectAttributes) throws Exception {

		RE_Register register = findRegister(registerName);
		if (register == null) {
			redirectAttributes.addFlashAttribute("registerName", registerName);
			return "registry/register_notfound";
		}
		
		security.assertHasEntityRelatedRole(SUBMITTER_ROLE_PREFIX, register);

		model.addAttribute("register", register);

		if (bindingResult.hasErrors()) {
			model.addAttribute("isNew", "true");
			
			Set<RE_ItemClass> itemClasses = register.getContainedItemClasses();
			model.addAttribute("itemClasses", itemClasses);
			return "proposal";
		}
		
		String itemClassUuid = allParams.get("itemClass");
		proposal = bindAdditionalAttributes(proposal, servletRequest/*, itemClassUuid*/);
		
		Addition addition = itemService.proposeAddition(proposal);
		redirectAttributes.addFlashAttribute("createdItem", addition.getItem().getUuid().toString());
		
		return "redirect:/register/" + registerName + "/proposals";
	}

	protected RegisterItemProposalDTO bindAdditionalAttributes(RegisterItemProposalDTO proposal, ServletRequest servletRequest) {
		RE_ItemClass selectedItemClass = itemClassRepository.findOne(proposal.getItemClassUuid());
		ItemClassConfiguration itemClassConfiguration = itemClassRegistry.getConfiguration(selectedItemClass.getName());
		
		if (itemClassConfiguration != null) {
			try {
				@SuppressWarnings("unchecked")
				Class<? extends RegisterItemProposalDTO> dtoClass = 
						(Class<? extends RegisterItemProposalDTO>)this.getClass().getClassLoader().loadClass(itemClassConfiguration.getDtoClass());
				proposal = BeanUtils.instantiateClass(dtoClass);
				ServletRequestDataBinder binder = new ServletRequestDataBinder(proposal);
				binder.bind(servletRequest);
				
//				proposal.setItemClassUuid(UUID.fromString(itemClassUuid));
			}
			catch (ClassNotFoundException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}
		return proposal;
	}	

	
	public static class SupersessionState {
		private String step;
		private Set<RegisterItemViewBean> validItems;
		private RE_SubmittingOrganization sponsor;
		private final Set<RegisterItemProposalDTO> newSupersedingItems = new HashSet<RegisterItemProposalDTO>();
		private final Set<RegisterItemViewBean> existingSupersedingItems = new HashSet<RegisterItemViewBean>();
		private final Set<RegisterItemViewBean> supersededItems = new HashSet<RegisterItemViewBean>();
		private String justification;
		private String registerManagerNotes;
		private String controlBodyNotes;
		
		public SupersessionState(RE_Register register, RE_SubmittingOrganization sponsor, RegisterItemService itemService) {
			this.sponsor = sponsor;
			
			step = "supersededItems";
			
			validItems = new HashSet<RegisterItemViewBean>();			
			Set<RE_RegisterItem> validItemsDb = itemService.findByRegisterAndStatus(register, RE_ItemStatus.VALID);
			for (RE_RegisterItem validItem : validItemsDb) {
				validItems.add(new RegisterItemViewBean(validItem));
			}
		}
		
		public SupersessionState(Supersession proposal, RegisterItemService itemService) {
			this.sponsor = proposal.getSponsor();
			step = "supersededItems";
			
			validItems = new HashSet<RegisterItemViewBean>();			
			Set<RE_RegisterItem> validItemsDb = itemService.findByRegisterAndStatus(proposal.getTargetRegister(), RE_ItemStatus.VALID);
			for (RE_RegisterItem validItem : validItemsDb) {
				validItems.add(new RegisterItemViewBean(validItem));
			}
			
			for (RE_RegisterItem supersededItem : proposal.getSupersededItems()) {
				this.addSupersededItem(supersededItem);
			}
			for (RE_RegisterItem supersedingItem : proposal.getSupersedingItems()) {
				this.addExistingSupersedingItem(supersedingItem);
			}
			
			this.justification = proposal.getJustification();
			this.registerManagerNotes = proposal.getRegisterManagerNotes();
			this.controlBodyNotes = proposal.getControlBodyNotes();
		}

		/**
		 * @return the step
		 */
		public String getStep() {
			return step;
		}

		/**
		 * @param step the step to set
		 */
		public void setStep(String step) {
			this.step = step;
		}

		/**
		 * @return the validItems
		 */
		public Set<RegisterItemViewBean> getValidItems() {
			return validItems;
		}

		/**
		 * @param validItems the validItems to set
		 */
		public void setValidItems(Set<RegisterItemViewBean> validItems) {
			this.validItems = validItems;
		}

		/**
		 * @return the sponsor
		 */
		public RE_SubmittingOrganization getSponsor() {
			return sponsor;
		}

		/**
		 * @param sponsor the sponsor to set
		 */
		public void setSponsor(RE_SubmittingOrganization sponsor) {
			this.sponsor = sponsor;
		}

		/**
		 * @return the newSupersedingItems
		 */
		public Set<RegisterItemProposalDTO> getNewSupersedingItems() {
			return Collections.unmodifiableSet(newSupersedingItems);
		}
		
		public void addNewSupersedingItem(RegisterItemProposalDTO newItem) {
			this.newSupersedingItems.add(newItem);
		}
		
		public Set<RegisterItemViewBean> getExistingSupersedingItems() {
			return Collections.unmodifiableSet(existingSupersedingItems);
		}

		public void addExistingSupersedingItem(RE_RegisterItem existingItem) {
			this.existingSupersedingItems.add(new RegisterItemViewBean(existingItem));
		}
		
		public void removeSupersedingItem(UUID uuid) {
			for (RegisterItemProposalDTO newItem : this.newSupersedingItems) {
				if (newItem.getItemUuid().equals(uuid)) {
					this.newSupersedingItems.remove(newItem);
					return;
				}
			}
			
			for (RegisterItemViewBean existingItem : this.existingSupersedingItems) {
				if (existingItem.getUuid().equals(uuid)) {
					this.existingSupersedingItems.remove(existingItem);
					return;
				}
			}
		}
		
		public void removeAllSupersededItems() {
			this.supersededItems.clear();
		}
		
		/**
		 * @return the supersededItems
		 */
		public Set<RegisterItemViewBean> getSupersededItems() {
			return Collections.unmodifiableSet(supersededItems);
		}
		
		public List<UUID> getSupersededItemUuids() {
			List<UUID> result = new ArrayList<UUID>();
			for (RegisterItemViewBean rvb : supersededItems) {
				result.add(rvb.getUuid());
			}
			
			return Collections.unmodifiableList(result);
		}
		
		public void addSupersededItem(RE_RegisterItem supersededItem) {
			if (!this.getSupersededItemUuids().contains(supersededItem.getUuid())) {
				supersededItems.add(new RegisterItemViewBean(supersededItem));
			}
		}
		
		public void removeSupersededItem(UUID uuid) {
			supersededItems.remove(uuid);
		}

		/**
		 * @return the justification
		 */
		public String getJustification() {
			return justification;
		}

		/**
		 * @param justification the justification to set
		 */
		public void setJustification(String justification) {
			this.justification = justification;
		}

		/**
		 * @return the registerManagerNotes
		 */
		public String getRegisterManagerNotes() {
			return registerManagerNotes;
		}

		/**
		 * @param registerManagerNotes the registerManagerNotes to set
		 */
		public void setRegisterManagerNotes(String registerManagerNotes) {
			this.registerManagerNotes = registerManagerNotes;
		}

		/**
		 * @return the controlBodyNotes
		 */
		public String getControlBodyNotes() {
			return controlBodyNotes;
		}

		/**
		 * @param controlBodyNotes the controlBodyNotes to set
		 */
		public void setControlBodyNotes(String controlBodyNotes) {
			this.controlBodyNotes = controlBodyNotes;
		}
	}
}
