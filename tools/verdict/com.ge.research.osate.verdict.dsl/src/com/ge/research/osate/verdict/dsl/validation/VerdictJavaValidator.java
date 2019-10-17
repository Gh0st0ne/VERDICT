/*
 * generated by Xtext
 */
package com.ge.research.osate.verdict.dsl.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.xtext.resource.impl.ResourceDescriptionsProvider;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.CheckType;
import org.osate.aadl2.AadlPackage;
import org.osate.aadl2.AnnexLibrary;
import org.osate.aadl2.AnnexSubclause;
import org.osate.aadl2.Classifier;
import org.osate.aadl2.PublicPackageSection;
import org.osate.aadl2.Subcomponent;
import org.osate.aadl2.SubcomponentType;
import org.osate.aadl2.SystemImplementation;
import org.osate.aadl2.SystemType;
import org.osate.xtext.aadl2.properties.validation.PropertiesJavaValidator;

import com.ge.research.osate.verdict.dsl.ThreatModelUtil;
import com.ge.research.osate.verdict.dsl.ThreatModelUtil.FieldTypeResult;
import com.ge.research.osate.verdict.dsl.VerdictUtil;
import com.ge.research.osate.verdict.dsl.type.VerdictType;
import com.ge.research.osate.verdict.dsl.type.VerdictVariable;
import com.ge.research.osate.verdict.dsl.verdict.CRVAssumption;
import com.ge.research.osate.verdict.dsl.verdict.CyberMission;
import com.ge.research.osate.verdict.dsl.verdict.CyberRel;
import com.ge.research.osate.verdict.dsl.verdict.CyberReq;
import com.ge.research.osate.verdict.dsl.verdict.Event;
import com.ge.research.osate.verdict.dsl.verdict.Intro;
import com.ge.research.osate.verdict.dsl.verdict.LPort;
import com.ge.research.osate.verdict.dsl.verdict.SafetyRel;
import com.ge.research.osate.verdict.dsl.verdict.SafetyReq;
import com.ge.research.osate.verdict.dsl.verdict.Statement;
import com.ge.research.osate.verdict.dsl.verdict.TargetLikelihood;
import com.ge.research.osate.verdict.dsl.verdict.ThreatDatabase;
import com.ge.research.osate.verdict.dsl.verdict.ThreatDefense;
import com.ge.research.osate.verdict.dsl.verdict.ThreatEqualContains;
import com.ge.research.osate.verdict.dsl.verdict.ThreatModel;
import com.ge.research.osate.verdict.dsl.verdict.ThreatStatement;
import com.ge.research.osate.verdict.dsl.verdict.Var;
import com.ge.research.osate.verdict.dsl.verdict.Verdict;
import com.ge.research.osate.verdict.dsl.verdict.VerdictPackage;
import com.ge.research.osate.verdict.dsl.verdict.VerdictThreatModels;
import com.google.inject.Inject;

/**
 * This class contains custom validation rules.
 *
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
public class VerdictJavaValidator extends PropertiesJavaValidator {
	@Inject
	private ResourceDescriptionsProvider indexProvider;

	// This overridden method is crucial
	@Override
	public List<EPackage> getEPackages() {
		List<EPackage> result = new ArrayList<>(super.getEPackages());
		result.add(VerdictPackage.eINSTANCE);
		result.add(EPackage.Registry.INSTANCE.getEPackage("http://aadl.info/AADL/2.0"));
		return result;
	}

	// This allows us to provide validation checks for annexes in AADL files
	@Override
	public boolean isResponsible(Map<Object, Object> context, EObject object) {
		return (object.eClass().getEPackage() == VerdictPackage.eINSTANCE) || object instanceof AadlPackage;
	}

	/**
	 * Check that cyber relations/requirements have unique, non-empty IDs
	 * and they are only located in subsystems/top-level systems, respectively.
	 *
	 * @param statement
	 */
	@Check(CheckType.FAST)
	public void checkStatement(Statement statement) {
		Set<String> otherIds = new HashSet<>();
		Set<SubcomponentType> subcomponents = new HashSet<>();

		String statementType;
		boolean shouldBeSubcomponent;
		if (statement instanceof CyberRel) {
			statementType = "Cyber relation";
			shouldBeSubcomponent = true;
		} else if (statement instanceof CyberReq) {
			statementType = "Cyber requirement";
			shouldBeSubcomponent = false;
		} else if (statement instanceof CyberMission) {
			statementType = "Mission";
			shouldBeSubcomponent = false;
		} else if (statement instanceof SafetyReq) {
			statementType = "Safety requirement";
			shouldBeSubcomponent = false;
		} else if (statement instanceof SafetyRel) {
			statementType = "Safety relation";
			shouldBeSubcomponent = true;
		} else if (statement instanceof Event) {
			statementType = "Event";
			shouldBeSubcomponent = true;
		} else {
			throw new RuntimeException(
					"statement is not CyberRel, or CyberReq, or Mission, or SafetyReq, or SafetyRel, or Event!?");
		}

		if (statement.getId().length() == 0) {
			error(statementType + " must specify an ID", VerdictPackage.Literals.STATEMENT__ID);
			return;
		}

		// Find enclosing system
		EObject container = statement;
		while (container != null && !(container instanceof SystemType || container instanceof PublicPackageSection)) {
			container = container.eContainer();
		}
		if (container instanceof SystemType) {
			SystemType currentSystem = (SystemType) container;
			// Get enclosing file
			while (!(container instanceof PublicPackageSection)) {
				container = container.eContainer();
			}
			PublicPackageSection pack = (PublicPackageSection) container;
			// Find all systems
			for (Classifier cls : pack.getOwnedClassifiers()) {
				if (cls instanceof SystemType) {
					SystemType system = (SystemType) cls;
					// Get all verdict annexes for this system
					for (AnnexSubclause annex : system.getOwnedAnnexSubclauses()) {
						if ("verdict".equals(annex.getName())) {
							Verdict subclause = VerdictUtil.getVerdict(annex);

							// Get all other statement IDs
							for (Statement other : subclause.getElements()) {
								// Don't double-count self
								if (!statement.equals(other)) {
									otherIds.add(other.getId());
								}
							}
						}
					}
				} else if (cls instanceof SystemImplementation) {
					// Grab the dependency/subcomponent tree
					SystemImplementation systemImpl = (SystemImplementation) cls;
					for (Subcomponent sub : systemImpl.getAllSubcomponents()) {
						subcomponents.add(sub.getSubcomponentType());
					}
				}
			}

			/*
			 * A system is a top-level system if it is not a subcomponent
			 * of any other system (cyber requirements are valid).
			 *
			 * If it is a subcomponent of another system, then it is a
			 * subcomponent (cyber relations are valid).
			 */
			boolean isSubcomponent = subcomponents.contains(currentSystem);

			if (isSubcomponent && !shouldBeSubcomponent) {
				error(statementType + " not allowed in subcomponent system");
			} else if (!isSubcomponent && shouldBeSubcomponent) {
				error(statementType + " not allowed in top-level system");
			}

			if (otherIds.contains(statement.getId())) {
				error("Duplicate ID " + statement.getId(), VerdictPackage.Literals.STATEMENT__ID);
			}
		}

		// Perform extra checks for missions
		if (statement instanceof CyberMission) {
			checkMission((CyberMission) statement);
		}

		// And cyber reqs
		if (statement instanceof CyberReq) {
			checkCyberReq((CyberReq) statement);
		}
	}

	/**
	 * Called by checkStatement().
	 *
	 * Check that cyber requirements listed in mission are valid and unique.
	 *
	 * @param mission
	 */
	private void checkMission(CyberMission mission) {
		// Get all cyber reqs defined
		Set<String> cyberReqs = VerdictUtil.getCyberReqs(mission);

		// Count reqs to check for duplicates
		Map<String, Integer> reqCounts = new HashMap<>();
		for (String req : mission.getCyberReqs()) {
			if (reqCounts.containsKey(req)) {
				reqCounts.put(req, reqCounts.get(req) + 1);
			} else {
				reqCounts.put(req, 1);
			}
		}

		int pos = 0;
		for (String req : mission.getCyberReqs()) {
			if (!cyberReqs.contains(req)) {
				error("Undefined cyber requirement: " + req, VerdictPackage.Literals.CYBER_MISSION__CYBER_REQS, pos);
			} else if (reqCounts.get(req) > 1) {
				warning("Duplicate cyber requirement: " + req, VerdictPackage.Literals.CYBER_MISSION__CYBER_REQS, pos);
			}
			pos++;
		}
	}

	/**
	 * Called by checkStatement().
	 *
	 * @param req
	 */
	@Check(CheckType.FAST)
	private void checkCyberReq(CyberReq req) {
		if (req.getSeverity() != null && req.isHasTargetLikelihood()) {
			TargetLikelihood shouldHaveTargetLikelihood;
			switch (req.getSeverity()) {
			case SEVERITY_CATASTROPHIC:
				shouldHaveTargetLikelihood = TargetLikelihood.TL_CATASTROPHIC;
				break;
			case SEVERITY_HAZARDOUS:
				shouldHaveTargetLikelihood = TargetLikelihood.TL_HAZARDOUS;
				break;
			case SEVERITY_MAJOR:
				shouldHaveTargetLikelihood = TargetLikelihood.TL_MAJOR;
				break;
			case SEVERITY_MINOR:
				shouldHaveTargetLikelihood = TargetLikelihood.TL_MINOR;
				break;
			case SEVERITY_NONE:
				shouldHaveTargetLikelihood = TargetLikelihood.TL_NONE;
				break;
			default:
				throw new RuntimeException("someone defined an additional severity");
			}

			if (req.getTargetLikelihood() != null && !req.getTargetLikelihood().equals(shouldHaveTargetLikelihood)) {
				error("Target likelihood for severity " + req.getSeverity().getLiteral() + " should be "
						+ shouldHaveTargetLikelihood.getLiteral(),
						VerdictPackage.Literals.CYBER_REQ__TARGET_LIKELIHOOD);
			}
		}
	}

	/**
	 * Check that ports are valid input or output ports for the enclosing
	 * system, depending on what is required for the context.
	 *
	 * @param port
	 */
	@Check(CheckType.FAST)
	public void checkLPort(LPort port) {
		VerdictUtil.AvailablePortsInfo info = VerdictUtil.getAvailablePorts(port, false);

		/*
		 * If this condition is not met, then there is an error being
		 * shown by a different validation check, so we don't have to do
		 * anything here.
		 */
		if (info.system != null) {
			if (!info.availablePorts.contains(port.getPort())) {
				if (info.isInput) {
					error("Not an input data port for system " + info.system.getName(),
							VerdictPackage.Literals.LPORT__PORT);
				} else {
					error("Not an output data port for system " + info.system.getName(),
							VerdictPackage.Literals.LPORT__PORT);
				}
			}
		}
	}

	/**
	 * Check that the var ID is in scope and that each field is
	 * defined for the type of that var.
	 *
	 * @param var
	 */
	@Check(CheckType.FAST)
	public void checkVar(Var var) {
		if (var.getIds() != null && var.getIds().size() > 0) {
			// Get the type of the var
			FieldTypeResult result = ThreatModelUtil.getVarType(var, indexProvider);
			if (!result.var.isPresent()) {
				error("Undefined var: " + result.varName, VerdictPackage.Literals.VAR__ID);
			} else if (!result.var.get().getType().isPresent()) {
				// Variable type is undefined; this is validated in checkIntro()
			} else if (!result.type.isPresent()) {
				// Display the error at the correct field by using fieldIndex
				error(result.lastField + " is not a field of type " + result.var.get().getType().get().getShortName(),
						VerdictPackage.Literals.VAR__IDS, result.fieldIndex);
			}
		}
	}

	/**
	 * Check that the new ID doesn't shadow a previously-introduced variable
	 * and that the type of the introduced variable is valid
	 *
	 * @param intro
	 */
	@Check(CheckType.FAST)
	public void checkIntro(Intro intro) {
		EObject scopeParent = ThreatModelUtil.getContainerForClasses(intro, ThreatModelUtil.INTRO_SCOPE_PARENT_CLASSES);

		// Check that the new ID doesn't shadow a previously-introduced variable

		String id = intro.getId();
		// Find a variable in scope with the same ID
		Optional<VerdictVariable> shadowVar = ThreatModelUtil.getScope(scopeParent, indexProvider).stream()
				.filter(v -> v.getId().equals(id)).findFirst();
		if (shadowVar.isPresent()) {
			warning("Shadowing var: " + id, VerdictPackage.Literals.INTRO__ID);
		}

		// Check that the type of the introduced variable is valid

		Optional<VerdictType> type = ThreatModelUtil.getIntroType(intro, indexProvider);
		if (!type.isPresent()) {
			error("Invalid type: " + intro.getType(), VerdictPackage.Literals.INTRO__TYPE);
		}
	}

	/**
	 * Check that RHS and LHS in equality are of the same type.
	 * Check that LHS type is list of RHS type in contains.
	 *
	 * @param equalContains
	 */
	@Check(CheckType.FAST)
	public void checkThreatEqualContains(ThreatEqualContains equalContains) {
		FieldTypeResult left = ThreatModelUtil.getVarType(equalContains.getLeft(), indexProvider);
		FieldTypeResult right = ThreatModelUtil.getVarType(equalContains.getRight(), indexProvider);

		// Note: the other error-ing cases not covered here have
		// their errors reported elsewhere

		if (equalContains.isEqual()) {
			if (left.type.isPresent() && right.type.isPresent()) {
				// Both are typed
				if (!left.type.get().equals(right.type.get())) {
					error("Incompatible types: " + left.type.get().getShortName() + " and "
							+ right.type.get().getShortName());
				}
			} else if (left.type.isPresent()) {
				// Left is typed
				if (equalContains.getRight().getIds() != null && equalContains.getRight().getIds().isEmpty()) {
					// Possible value
					if (!left.type.get().isValue(right.varName)) {
						error("Invalid value " + right.varName + " for type " + left.type.get().getShortName());
					}
				}
			} else if (right.type.isPresent()) {
				// Right is typed
				if (equalContains.getLeft().getIds() != null && equalContains.getLeft().getIds().isEmpty()) {
					// Possible value
					if (!right.type.get().isValue(left.varName)) {
						error("Invalid value " + left.varName + " for type " + right.type.get().getShortName());
					}
				}
			} else {
				// Neither is typed
				if (equalContains.getLeft().getIds().isEmpty() && equalContains.getRight().getIds().isEmpty()) {
					// Both are values
					error("Undefined var: " + left.varName, VerdictPackage.Literals.THREAT_EQUAL_CONTAINS__LEFT);
					error("Undefined var: " + right.varName, VerdictPackage.Literals.THREAT_EQUAL_CONTAINS__RIGHT);
				}
			}
		} else if (equalContains.isContains()) {
			if (left.type.isPresent() && right.type.isPresent()) {
				// Check list-of relation
				if (!left.type.get().isListOf(right.type.get())) {
					error("Incompatible types " + left.type.get().getShortName() + " and "
							+ right.type.get().getShortName() + ", left must be list of right ");
				}
			} else {
				/**
				 * Make sure vars are defined.
				 *
				 * This check can only happen here because standalone vars
				 * might be bare value constants.
				 */
				if (!left.type.isPresent() && equalContains.getLeft().getIds() != null
						&& equalContains.getLeft().getIds().isEmpty()) {
					error("Undefined var: " + left.varName, VerdictPackage.Literals.THREAT_EQUAL_CONTAINS__LEFT);
				}
				if (!right.type.isPresent() && equalContains.getRight().getIds() != null
						&& equalContains.getRight().getIds().isEmpty()) {
					error("Undefined var: " + right.varName, VerdictPackage.Literals.THREAT_EQUAL_CONTAINS__RIGHT);
				}
			}
		}
	}

	/**
	 * Check that IDS are unique and non-empty and that top-level intro is a system.
	 * Check that assumptions are unique.
	 *
	 * @param threatModel
	 */
	@Check(CheckType.FAST)
	public void checkThreatModel(ThreatModel threatModel) {
		// Check that top-level intro is a system

		if (threatModel.getIntro().getType() != null && !threatModel.getIntro().getType().equals("system")
				&& !threatModel.getIntro().getType().equals("connection")) {
			error("Top-level quantified variable must be a system or connection",
					VerdictPackage.Literals.THREAT_MODEL__INTRO);
		}

		// Check ID non-empty

		if (threatModel.getId().length() == 0) {
			error("Threat model must specify an ID", VerdictPackage.Literals.THREAT_MODEL__ID);
		} else {
			// Check IDs unique

			Set<String> otherIds = new HashSet<>();

			// Find AADL package
			EObject container = threatModel;
			while (container != null && !(container instanceof PublicPackageSection)) {
				container = container.eContainer();
			}
			if (container instanceof PublicPackageSection) {
				// Find all verdict annex libraries
				for (AnnexLibrary library : ((PublicPackageSection) container).getOwnedAnnexLibraries()) {
					if ("verdict".equals(library.getName())) {
						// Find all other threat model declarations
						for (ThreatStatement other : ThreatModelUtil.getVerdictThreatModels(library).getStatements()) {
							if (other instanceof ThreatModel && !threatModel.equals(other)) {
								otherIds.add(((ThreatModel) other).getId());
							}
						}
					}
				}
			}
			if (otherIds.contains(threatModel.getId())) {

				error("Duplicate ID " + threatModel.getId(), VerdictPackage.Literals.THREAT_MODEL__ID);
			}
		}

		// Check for duplicate assumptions

		Map<CRVAssumption, Integer> assumptionCounts = new HashMap<>();
		for (CRVAssumption assumption : threatModel.getAssumptions()) {
			if (assumptionCounts.containsKey(assumption)) {
				assumptionCounts.put(assumption, assumptionCounts.get(assumption) + 1);
			} else {
				assumptionCounts.put(assumption, 1);
			}
		}

		int pos = 0;
		for (CRVAssumption assumption : threatModel.getAssumptions()) {
			if (assumptionCounts.get(assumption) > 1) {
				warning("Duplicate assumption: " + assumption.getLiteral(),
						VerdictPackage.Literals.THREAT_MODEL__ASSUMPTIONS, pos);
			}
			pos++;
		}

		// Check valid threat database

		if (threatModel.getReference() != null) {
			// We say that a reference string is valid if there is a valid database ID
			// that is a prefix string of that reference string
			Set<String> definedDatabases = ThreatModelUtil.getDefinedThreatDatabases(threatModel);
			if (!definedDatabases.stream().anyMatch(database -> threatModel.getReference().startsWith(database))) {
				error("Undefined threat database: " + threatModel.getReference(),
						VerdictPackage.Literals.THREAT_MODEL__REFERENCE);
			}
		}
	}

	/**
	 * Check that threat defense names are non-empty and unique and that threat models are unique and defined.
	 *
	 * @param defense
	 */
	@Check(CheckType.FAST)
	public void checkThreatDefense(ThreatDefense defense) {
		List<String> otherDefenses = new ArrayList<>();

		AadlPackage top = ThreatModelUtil.getAadlPackage(defense);

		if (defense.getName().length() == 0) {
			error("Threat defense must specify an ID", VerdictPackage.Literals.THREAT_DEFENSE__NAME);
		} else {
			for (AnnexLibrary library : top.getOwnedPublicSection().getOwnedAnnexLibraries()) {
				if ("verdict".equals(library.getName())) {
					VerdictThreatModels threats = ThreatModelUtil.getVerdictThreatModels(library);
					for (ThreatStatement statement : threats.getStatements()) {
						if (statement instanceof ThreatDefense && statement != defense) {
							otherDefenses.add(((ThreatDefense) statement).getName());
						}
					}
				}
			}

			if (otherDefenses.contains(defense.getName())) {
				error("Duplicate ID: " + defense.getName(), VerdictPackage.Literals.THREAT_DEFENSE__NAME);
			}
		}

		// Check for duplicate threats

		Map<String, Integer> threatCounts = new HashMap<>();
		for (String threat : defense.getThreats()) {
			if (threatCounts.containsKey(threat)) {
				threatCounts.put(threat, threatCounts.get(threat) + 1);
			} else {
				threatCounts.put(threat, 1);
			}
		}

		int pos = 0;
		for (String threat : defense.getThreats()) {
			if (threatCounts.get(threat) > 1) {
				warning("Duplicate threat: " + threat, VerdictPackage.Literals.THREAT_DEFENSE__THREATS, pos);
			}
			pos++;
		}

		// Make sure threat models are defined

		Set<String> definedThreats = ThreatModelUtil.getDefinedThreatModels(defense);

		pos = 0;
		for (String threat : defense.getThreats()) {
			if (!definedThreats.contains(threat)) {
				error("Undefined threat: " + threat, VerdictPackage.Literals.THREAT_DEFENSE__THREATS, pos);
			}
			pos++;
		}
	}

	/**
	 * Check that threat database IDs are non-empty and unique.
	 *
	 * @param database
	 */
	@Check(CheckType.FAST)
	public void checkThreatDatabase(ThreatDatabase database) {
		List<String> otherDatabases = new ArrayList<>();

		AadlPackage top = ThreatModelUtil.getAadlPackage(database);

		if (database.getId().length() == 0) {
			error("Threat database must specify an ID", VerdictPackage.Literals.THREAT_DATABASE__ID);
		} else {
			for (AnnexLibrary library : top.getOwnedPublicSection().getOwnedAnnexLibraries()) {
				if ("verdict".equals(library.getName())) {
					VerdictThreatModels threats = ThreatModelUtil.getVerdictThreatModels(library);
					for (ThreatStatement statement : threats.getStatements()) {
						if (statement instanceof ThreatDatabase && statement != database) {
							otherDatabases.add(((ThreatDatabase) statement).getId());
						}
					}
				}
			}

			if (otherDatabases.contains(database.getId())) {
				error("Duplicate ID: " + database.getId(), VerdictPackage.Literals.THREAT_DATABASE__ID);
			}
		}
	}
}
