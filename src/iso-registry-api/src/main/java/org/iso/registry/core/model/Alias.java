package org.iso.registry.core.model;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import org.hibernate.envers.Audited;

@Access(AccessType.FIELD)
@Audited @Entity
public class Alias extends de.geoinfoffm.registry.core.Entity
{
	@ManyToOne
	private IdentifiedItem aliasedItem;
	
	@ManyToOne
	private NamingSystemItem namingSystem;
	
	@Column(name = "ALIAS", columnDefinition = "text")
	private String alias;
	
	@Column(name = "REMARKS", columnDefinition = "text")
	private String remarks;

	protected Alias() { }
	
	public Alias(String alias, NamingSystemItem namingSystem) {
		this.alias = alias;
		this.namingSystem = namingSystem;
	}

	public IdentifiedItem getAliasedItem() {
		return aliasedItem;
	}

	public void setAliasedItem(IdentifiedItem aliasedItem) {
		this.aliasedItem = aliasedItem;
	}

	public NamingSystemItem getNamingSystem() {
		return namingSystem;
	}

	public void setNamingSystem(NamingSystemItem namingSystem) {
		this.namingSystem = namingSystem;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}
	
}
