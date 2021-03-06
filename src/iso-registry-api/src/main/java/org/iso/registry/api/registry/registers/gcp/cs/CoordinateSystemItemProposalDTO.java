package org.iso.registry.api.registry.registers.gcp.cs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.iso.registry.api.IdentifiedItemProposalDTO;
import org.iso.registry.core.model.IdentifiedItem;
import org.iso.registry.core.model.cs.CartesianCoordinateSystemItem;
import org.iso.registry.core.model.cs.CoordinateSystemAxisItem;
import org.iso.registry.core.model.cs.CoordinateSystemItem;
import org.iso.registry.core.model.cs.EllipsoidalCoordinateSystemItem;
import org.iso.registry.core.model.cs.SphericalCoordinateSystemItem;
import org.isotc211.iso19135.RE_RegisterItem_Type;
import org.springframework.util.StringUtils;

import de.geoinfoffm.registry.api.RegisterItemProposalDTO;
import de.geoinfoffm.registry.api.soap.AbstractCoordinateSystemItemProposal_Type;
import de.geoinfoffm.registry.api.soap.AbstractRegisterItemProposal_Type;
import de.geoinfoffm.registry.api.soap.Addition_Type;
import de.geoinfoffm.registry.api.soap.CoordinateSystemAxisItemProposal_PropertyType;
import de.geoinfoffm.registry.core.model.Proposal;
import de.geoinfoffm.registry.core.model.iso19135.RE_RegisterItem;
import de.geoinfoffm.registry.core.model.iso19135.RE_SubmittingOrganization;

public class CoordinateSystemItemProposalDTO extends IdentifiedItemProposalDTO
{
	public enum CoordinateSystemType {
		AFFINE,
		CARTESIAN,
		CYLINDRICAL,
		ELLIPSOIDAL,
		ENGINEERING,
		IMAGE,
		LINEAR,
		POLAR,
		SPHERICAL,
		VERTICAL,
		USER_DEFINED
	}
	
	private CoordinateSystemType type;
	private List<CoordinateSystemAxisItemProposalDTO> axes = new ArrayList<CoordinateSystemAxisItemProposalDTO>();
	private String axisUuids;
	
	public CoordinateSystemItemProposalDTO() { }
	
	public CoordinateSystemItemProposalDTO(CoordinateSystemItem item) {
		super(item);
	}
	
//	public CoordinateSystemItemProposalDTO(CoordinateSystemType type, AxisDTO firstAxis, AxisDTO... otherAxes) {
//		this.type = type;
//		this.axes.add(firstAxis);
//		for (AxisDTO axis : otherAxes) {
//			this.axes.add(axis);
//		}
//	}

	public CoordinateSystemItemProposalDTO(AbstractCoordinateSystemItemProposal_Type itemDetails) {
		super(itemDetails);
	}

	public CoordinateSystemItemProposalDTO(Addition_Type proposal, RE_SubmittingOrganization sponsor) {
		super(proposal, sponsor);
		// TODO Auto-generated constructor stub
	}

	public CoordinateSystemItemProposalDTO(IdentifiedItem item) {
		super(item);
		// TODO Auto-generated constructor stub
	}

	public CoordinateSystemItemProposalDTO(Proposal proposal) {
		super(proposal);
	}

	public CoordinateSystemItemProposalDTO(RE_RegisterItem_Type item, RE_SubmittingOrganization sponsor) {
		super(item, sponsor);
		// TODO Auto-generated constructor stub
	}

	public CoordinateSystemItemProposalDTO(String itemClassName) {
		super(itemClassName);
		// TODO Auto-generated constructor stub
	}

	public CoordinateSystemType getType() {
		return this.type;
	}
	
	public void setType(CoordinateSystemType type) {
		this.type = type;
	}
	
	public List<CoordinateSystemAxisItemProposalDTO> getAxes() {
		return this.axes;
	}
	
	public void addAxis(CoordinateSystemAxisItemProposalDTO axis) {
		if (this.axes == null) {
			this.axes = new ArrayList<CoordinateSystemAxisItemProposalDTO>();
		}
		this.axes.add(axis);
	}
	
	public String getAxisUuids() {
		return axisUuids;
	}

	public void setAxisUuids(String axisUuids) {
		this.axisUuids = axisUuids;
	}

	@Override
	protected void initializeFromItemDetails(AbstractRegisterItemProposal_Type itemDetails) {
		super.initializeFromItemDetails(itemDetails);
	
		if (itemDetails instanceof AbstractCoordinateSystemItemProposal_Type) {
			AbstractCoordinateSystemItemProposal_Type xmlProposal = (AbstractCoordinateSystemItemProposal_Type) itemDetails;
	
			
			for (CoordinateSystemAxisItemProposal_PropertyType axesProperty : xmlProposal.getAxes()) {
				if (axesProperty != null) {
					final CoordinateSystemAxisItemProposalDTO dto;
					if (axesProperty.isSetCoordinateSystemAxisItemProposal()) {
						dto = new CoordinateSystemAxisItemProposalDTO(axesProperty.getCoordinateSystemAxisItemProposal());
					}
					else if (axesProperty.isSetUuidref()) {
						dto = new CoordinateSystemAxisItemProposalDTO();
						dto.setReferencedItemUuid(UUID.fromString(axesProperty.getUuidref()));
					}
					else {
						throw new RuntimeException("unexpected reference");
					}
					this.getAxes().add(dto);
				}
			}
		}	
	}

	@Override
	public void setAdditionalValues(RE_RegisterItem item, EntityManager entityManager) {
		super.setAdditionalValues(item, entityManager);
		
		if (item instanceof CoordinateSystemItem) {
			CoordinateSystemItem cs = (CoordinateSystemItem)item;

			if (!StringUtils.isEmpty(this.axisUuids)) {
				for (String uuidText : StringUtils.delimitedListToStringArray(this.axisUuids, ","," ")) {
					UUID uuid = UUID.fromString(uuidText);
					CoordinateSystemAxisItem axis = entityManager.find(CoordinateSystemAxisItem.class, uuid);
					cs.addAxis(axis);
				}
			}
			else if (this.getAxes() != null) {
//				for (AxisDTO axisDto : this.getAxes()) {
//					CoordinateSystemAxisItem axis = entityManager.find(CoordinateSystemAxisItem.class, axisDto.getAxisUuid());
//					cs.addAxis(axis);
//				}
				for (CoordinateSystemAxisItemProposalDTO axisDto : this.getAxes()) {
					if (axisDto.getReferencedItemUuid() == null) {
						new Object();
					}
					CoordinateSystemAxisItem axisItem = entityManager.find(CoordinateSystemAxisItem.class, axisDto.getReferencedItemUuid());
					cs.getAxes().add(axisItem);
				}
			}
		}
	}

	@Override
	public void loadAdditionalValues(RE_RegisterItem item) {
		super.loadAdditionalValues(item);
		
		if (item instanceof CoordinateSystemItem) {
			CoordinateSystemItem cs = (CoordinateSystemItem)item;
			if (item instanceof CartesianCoordinateSystemItem) {
				this.setType(CoordinateSystemType.CARTESIAN);
			}
			else if (item instanceof EllipsoidalCoordinateSystemItem) {
				this.setType(CoordinateSystemType.ELLIPSOIDAL);
			}
			else if (item instanceof SphericalCoordinateSystemItem) {
				this.setType(CoordinateSystemType.SPHERICAL);
			}
			else {
				this.setType(CoordinateSystemType.USER_DEFINED);
			}
			
			if (cs.getAxes() != null) {
				List<String> axisUuidList = new ArrayList<>();
				for (CoordinateSystemAxisItem axis : cs.getAxes()) {
//					this.addAxis(new AxisDTO(axis.getUuid(), axis.getName()));
					this.addAxis(new CoordinateSystemAxisItemProposalDTO(axis));
					axisUuidList.add(axis.getUuid().toString());
				}
				this.axisUuids = StringUtils.collectionToCommaDelimitedString(axisUuidList);
			}
		}
	}

	@Override
	public List<RegisterItemProposalDTO> getAggregateDependencies() {
		final List<RegisterItemProposalDTO> result = new ArrayList<RegisterItemProposalDTO>();
		result.addAll(super.getAggregateDependencies());
		result.addAll(this.getAxes());

		return super.findDependentProposals((RegisterItemProposalDTO[])result.toArray(new RegisterItemProposalDTO[result.size()]));
	}

	@Override
	public List<RegisterItemProposalDTO> getCompositeDependencies() {
		final List<RegisterItemProposalDTO> result = new ArrayList<RegisterItemProposalDTO>();
		result.addAll(super.getCompositeDependencies());
		
		return super.findDependentProposals((RegisterItemProposalDTO[])result.toArray(new RegisterItemProposalDTO[result.size()]));
	}

}