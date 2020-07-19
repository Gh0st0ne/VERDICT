package com.ge.verdict.gsn;

import static guru.nidi.graphviz.model.Factory.*;

import com.ge.verdict.vdm.VdmTranslator;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import verdict.vdm.vdm_model.*;

/** @author Saswata Paul */
public class CreateGSN {
    protected static int strategyCounter = 1;
    protected static int contextCounter = 1;
    protected static int solutionCounter = 1;

    /**
     * Entry method for the CreateGSN class
     * 
     * @param testXml
     * @param cyberOutput
     * @param safetyOutput
     * @param rootGoalId
     * @return a GsnNode
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public static GsnNode gsnCreator(File testXml, File cyberOutput, File safetyOutput, String rootGoalId)
            throws ParserConfigurationException, SAXException, IOException {

    	//The GsnNode to return
    	GsnNode returnFragment = new GsnNode();

        // Fetch the DeliveryDrone model from the XML
        Model xmlModel = VdmTranslator.unmarshalFromXml(testXml);

        // Get Document Builder for DOM parser
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Build CyberOutput Document
        Document cyberSoteria = builder.parse(cyberOutput);
        cyberSoteria.getDocumentElement().normalize();
        NodeList nListCyber = cyberSoteria.getElementsByTagName("Requirement");

        // Build SafetyOutput Document
        Document safetySoteria = builder.parse(safetyOutput);
        safetySoteria.getDocumentElement().normalize();
        NodeList nListSafety = safetySoteria.getElementsByTagName("Requirement");

 
        //determine if rootGoalId is a mission, cyber, or safety requirement 
        for (Mission aMission : xmlModel.getMission()) {
        	if (aMission.getId().equalsIgnoreCase(rootGoalId)){
        		returnFragment = populateMissionNode(aMission, xmlModel, nListCyber, nListSafety);  
        	}
        } 
        for (CyberReq aCyberReq : xmlModel.getCyberReq()) {
        	if (aCyberReq.getId().equalsIgnoreCase(rootGoalId)){
        		returnFragment = populateCyberRequirementNode(aCyberReq, xmlModel, nListCyber);  
        	}
        } 
        for (SafetyReq aSafetyReq : xmlModel.getSafetyReq()) {
        	if (aSafetyReq.getId().equalsIgnoreCase(rootGoalId)){
        		returnFragment = populateSafetyRequirementNode(aSafetyReq, xmlModel, nListSafety);  
        	}
        }        

        
        //return the GSN gragment
        return returnFragment;
    }

    /**
     * Produces a GSN fragment for each mission requirement 
     * in a model 
     *
     * @param model
     * @param cyberResults
     * @param safetyResults
     * @return a list of GsnNodes
     */
    public static List<GsnNode> getFragments(
            Model model, NodeList cyberResults, NodeList safetyResults) {
        // List of gsn nodes
        List<GsnNode> fragments = new ArrayList<>();

        for (Mission mission : model.getMission()) {
            fragments.add(populateMissionNode(mission, model, cyberResults, safetyResults));
        }

        return fragments;
    }

    /**
     * Creates a GsnNode for the given mission
     *
     * @param mission
     * @param model
     * @param cyberResults
     * @param safetyResults
     * @return
     */
    public static GsnNode populateMissionNode(
            Mission mission, Model model, NodeList cyberResults, NodeList safetyResults) {

        // GsnNode to pack mission rootnode
        GsnNode missionNode = new GsnNode();

        // Populate the rootnode with the mission goal
        missionNode.setNodeType("goal");
        missionNode.setNodeId("GOAL_" + mission.getId());

        System.out.println("Mission: " + mission.getId());
        // to set goal of missionNode
        Goal missionGoal = new Goal();
        missionGoal.setDisplayText(mission.getDescription());
        missionNode.setGoal(missionGoal);

        //add contexts to missionNode
        List<String> missionContext = new ArrayList<>();
        missionContext.add(model.getName());
        System.out.println("-------------------" + missionContext);
        missionNode.getInContextOf().addAll(addContextPorts(missionContext, model));

        // create a strategy node to support the mission goal
        GsnNode strategyNode = new GsnNode();
        strategyNode.setNodeType("strategy");

        String strategyId = "STRATEGY_" + Integer.toString(strategyCounter);
        strategyCounter++;
        strategyNode.setNodeId(strategyId);
        System.out.println("----Strategy: " + strategyNode.getNodeId());

        String strategyText = "Argument: By conjunction of subgoals:\\n";

        // add requirements to supportedBy of StrategyNode
        for (String subReqId : mission.getCyberReqs()) {
            strategyText = strategyText + subReqId + " ";

            // check if reqId is Cyber or Safety req
            for (CyberReq cyberReq : model.getCyberReq()) {
                if (cyberReq.getId().equals(subReqId)) {
                    System.out.println("--------Requirement: " + subReqId);
                    strategyNode
                            .getSupportedBy()
                            .add(populateCyberRequirementNode(cyberReq, model, cyberResults));
                } else continue;
            }
            for (SafetyReq safetyReq : model.getSafetyReq()) {
                if (safetyReq.getId().equals(subReqId)) {
                    System.out.println("--------Requirement: " + subReqId);
                    strategyNode
                            .getSupportedBy()
                            .add(populateSafetyRequirementNode(safetyReq, model, safetyResults));
                } else continue;
            }
        }

        // to pack strategy for strategyNode
        Strategy strat = new Strategy();

        // add strategy status
        strat.setStatus(true);
        for (GsnNode subNode : strategyNode.getSupportedBy()) {
            if (!subNode.getGoal().getStatus()) {
                strat.setStatus(false);
            }
        }

        // add strategy text to display
        strat.setDisplayText(strategyText);

        // add strategy to strategyNode
        strategyNode.setStrategy(strat);

        // INCOMPLETE: add contexts to strategyNode

        // add strategyNode to supportedBy of missionNode
        missionNode.getSupportedBy().add(strategyNode);

        // setting reqNode status
        missionNode.getGoal().setStatus(strategyNode.getStrategy().getStatus());

        return missionNode;
    }

    /**
     * Creates a GSN node for the given cyber subrequirement
     *
     * @param cyberReq
     * @param model
     * @param cyberResults
     * @return
     */
    public static GsnNode populateCyberRequirementNode(
            CyberReq cyberReq, Model model, NodeList cyberResults) {
        // GsnNode to pack requirement rootnode
        GsnNode reqNode = new GsnNode();

        // Populate the rootnode with the requirement subgoal
        reqNode.setNodeType("goal");

        reqNode.setNodeId("GOAL_" + cyberReq.getId());
        // to populate goal of node
        Goal reqNodeGoal = new Goal();
        reqNodeGoal.setDisplayText(cyberReq.getDescription());
        reqNode.setGoal(reqNodeGoal);

        //Add context to reqNode
        List<String> reqContextPorts = getCyberExprPorts(cyberReq.getCondition());
        reqNode.getInContextOf().addAll(addContextPorts(reqContextPorts, model));

        // create a strategy node to support the requirement subgoal
        GsnNode strategyNode = new GsnNode();

        strategyNode.setNodeType("strategy");
        String strategyId = "STRATEGY_" + Integer.toString(strategyCounter);
        strategyCounter++;
        strategyNode.setNodeId(strategyId);
        System.out.println("------------Strategy: " + strategyNode.getNodeId());

        // to populate Strategy of strategyNode
        Strategy strat = new Strategy();
        strat.setDisplayText("Argument: By Soteria++ analysis\\nof attack-defense trees");

        // add a solution to the supportedBy of strategy
        GsnNode solutionNode = populateSolutionNode(cyberReq.getId(), cyberResults);
        strategyNode.getSupportedBy().add(solutionNode);

        // setting strategy status
        strat.setStatus(solutionNode.getSolution().getStatus());

        // adding strategy to strategyNode
        strategyNode.setStrategy(strat);

        // add strategyNode to supportedBy of reqNode
        reqNode.getSupportedBy().add(strategyNode);

        // setting reqNode status
        reqNode.getGoal().setStatus(strategyNode.getStrategy().getStatus());

        return reqNode;
    }

    /**
     * Creates a GSN node for the given safety subrequirement
     *
     * @param safetyReq
     * @param model
     * @param safetyResults
     * @return
     */
    public static GsnNode populateSafetyRequirementNode(
            SafetyReq safetyReq, Model model, NodeList safetyResults) {
        // GsnNode to pack requirement rootnode
        GsnNode reqNode = new GsnNode();

        // Populate the rootnode with the requirement subgoal
        reqNode.setNodeType("goal");
        reqNode.setNodeId("GOAL_" + safetyReq.getId());
        // to populate goal of node
        Goal reqNodeGoal = new Goal();
        reqNodeGoal.setDisplayText(safetyReq.getDescription());
        reqNode.setGoal(reqNodeGoal);

        //Add context to reqNode
        List<String> reqContextPorts = getSafetyReqExprPorts(safetyReq.getCondition());
        reqNode.getInContextOf().addAll(addContextPorts(reqContextPorts, model));

        // create a strategy node to support the requirement subgoal
        GsnNode strategyNode = new GsnNode();

        strategyNode.setNodeType("strategy");
        String strategyId = "STRATEGY_" + Integer.toString(strategyCounter);
        strategyCounter++;
        strategyNode.setNodeId(strategyId);
        System.out.println("------------Strategy: " + strategyNode.getNodeId());

        // to populate Strategy of strategyNode
        Strategy strat = new Strategy();
        strat.setDisplayText("Argument: By Soteria++ analysis\\nof fault trees");

        // add a solution to the supportedBy of strategy
        GsnNode solutionNode = populateSolutionNode(safetyReq.getId(), safetyResults);
        strategyNode.getSupportedBy().add(solutionNode);

        // setting strategy status
        strat.setStatus(solutionNode.getSolution().getStatus());

        // adding strategy to strategyNode
        strategyNode.setStrategy(strat);

        // add strategyNode to supportedBy of reqNode
        reqNode.getSupportedBy().add(strategyNode);

        // setting reqNode status
        reqNode.getGoal().setStatus(strategyNode.getStrategy().getStatus());

        return reqNode;
    }

    /**
     * Creates a GSN node for a solution
     *
     * @param safetyReq
     * @param model
     * @param safetyResults
     * @return
     */
    public static GsnNode populateSolutionNode(String reqId, NodeList results) {
        // GsnNode to pack solution
        GsnNode solutionNode = new GsnNode();

        // Populate the rootnode with solution details

        solutionNode.setNodeType("solution");
        String solutionId = "SOLUTION_" + Integer.toString(solutionCounter);
        solutionCounter++;
        solutionNode.setNodeId(solutionId);
        // to set solution of solutionNode
        Solution sol = new Solution();
        sol.setDisplayText("Soteria++\\nminimal cutset\\nfor " + reqId);

        // Extract the probabilities from the soteria output xml
        String computed_p = "";
        String acceptable_p = "";

        for (int temp = 0; temp < results.getLength(); temp++) {
            Node node = results.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) node;
                if (eElement.getAttribute("label").equals(reqId)) {
                    computed_p = eElement.getAttribute("computed_p");
                    acceptable_p = eElement.getAttribute("acceptable_p");
                }
            }
        }

        // Check below if solution supports the requirement
        if (Double.parseDouble(computed_p) <= Double.parseDouble(acceptable_p)) {
            sol.setStatus(true);
            System.out.println("---------------- Found Solution -> " + true);
        } else {
            sol.setStatus(false);
            System.out.println("---------------- Found Solution -> " + false);
        }
        
        //setting hovering info for solution node
        String extraInfo = "Computed Probability = "+computed_p+"&#10;Acceptable Probability = "+acceptable_p;
        sol.setExtraInfo(extraInfo);

        // add sol to solutionNode
        solutionNode.setSolution(sol);

        return solutionNode;
    }

    /**
     * Creates a list of all port Ids from a CyberExpr
     *
     * @param cyberExpr
     * @return
     */
    public static List<String> getCyberExprPorts(CyberExpr cyberExpr) {
        // to pack return list
        List<String> returnList = new ArrayList<>();

        if (!(cyberExpr.getPort() == null)) { // base case: if a port
            returnList.add(cyberExpr.getPort().getName());
        } else {
            if (cyberExpr.getKind().value().equalsIgnoreCase("or")) { // if an or expression
                for (CyberExpr subExpr : cyberExpr.getOr().getExpr()) {
                    returnList.addAll(getCyberExprPorts(subExpr));
                }
            } else if (cyberExpr
                    .getKind()
                    .value()
                    .equalsIgnoreCase("and")) { // if an and expression
                for (CyberExpr subExpr : cyberExpr.getAnd().getExpr()) {
                    returnList.addAll(getCyberExprPorts(subExpr));
                }
            } else if (cyberExpr.getKind().value().equalsIgnoreCase("not")) { // if a not expression
                returnList.addAll(getCyberExprPorts(cyberExpr.getNot()));
            }
        }

        return returnList;
    }

    /**
     * Creates a list of all portIds from a SafetyReqExpr
     *
     * @param safetyExpr
     * @return
     */
    public static List<String> getSafetyReqExprPorts(SafetyReqExpr safetyExpr) {
        // to pack return list
        List<String> returnList = new ArrayList<>();

        if (!(safetyExpr.getPort() == null)) { // base case: if a port
            returnList.add(safetyExpr.getPort().getName());
        } else {
            if (safetyExpr.getKind().value().equalsIgnoreCase("or")) { // if an or expression
                for (SafetyReqExpr subExpr : safetyExpr.getOr().getExpr()) {
                    returnList.addAll(getSafetyReqExprPorts(subExpr));
                }
            } else if (safetyExpr
                    .getKind()
                    .value()
                    .equalsIgnoreCase("and")) { // if an and expression
                for (SafetyReqExpr subExpr : safetyExpr.getAnd().getExpr()) {
                    returnList.addAll(getSafetyReqExprPorts(subExpr));
                }
            } else if (safetyExpr
                    .getKind()
                    .value()
                    .equalsIgnoreCase("not")) { // if a not expression
                returnList.addAll(getSafetyReqExprPorts(safetyExpr.getNot()));
            }
        }

        return returnList;
    }

    /**
     * Creates and returns a list of GsnNodes that can be assigned as inContextOf for another Node
     *
     * @param contextNames
     * @return
     */
    public static List<GsnNode> addContextPorts(List<String> contextNames, Model model) {
        // Removing duplicates from context_names
        List<String> duplicateFreeContextNames = new ArrayList<>(new HashSet<>(contextNames));

        // A list of GsnNodes to contain the "inContextOf"
        List<GsnNode> inContextOf = new ArrayList<>();

        for (String context : duplicateFreeContextNames) {

            System.out.println("-------------------" + context);
            // a gsnNode for the context
            GsnNode contextNode = new GsnNode();

            // assigning values to the contextnode
            contextNode.setNodeType("context");
            String contextId = "CONTEXT_" + Integer.toString(contextCounter);
            contextCounter++;
            contextNode.setNodeId(contextId);
            
            //If context is a port, find the parent subsystem
            List<String> parentNames = new ArrayList<>();;
            for(ComponentType cType : model.getComponentType()) {
            	for(Port port : cType.getPort()) {
            		if (port.getName().equalsIgnoreCase(context)) {
            			parentNames.add(cType.getName());
            		}
            	}
            }

            // pack the context
            Context nodeContext = new Context();
            nodeContext.setDisplayText(context);
            if(!(parentNames.isEmpty())) {
            	String extraInfo = "Belongs to:";
            	for(String parent : parentNames) {
            		extraInfo= extraInfo+"&#10;"+parent;
            	}
            	nodeContext.setExtraInfo(extraInfo);
            }

            contextNode.setContext(nodeContext);

            // adding to the list
            inContextOf.add(contextNode);
        }

        return inContextOf;
    }
}
