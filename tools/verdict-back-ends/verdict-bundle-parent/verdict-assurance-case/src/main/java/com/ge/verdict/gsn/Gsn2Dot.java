package com.ge.verdict.gsn;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.davidmoten.text.utils.WordWrap;

/** @author Saswata Paul */
public class Gsn2Dot {

    // To store all the nodes in the GSN Fragment
    protected static List<GsnNode> allNodes = new ArrayList<>();

    /**
     * Takes a GsnNode and a file address and creates a dot file for the GSN fragment
     *
     * @param GSN
     * @param fout
     * @throws IOException
     */
    public static void createDot(GsnNode GSN, File fout) throws IOException {

        // get all nodes in the GSN fragment
        getAllNodes(allNodes, GSN);

        // Create fileoutputstreams for writing to the file
        FileOutputStream fos = new FileOutputStream(fout);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

        // Start the graph
        bw.write("digraph ASSURANCE_CASE{");
        bw.newLine();
        bw.write("ratio = 0.5;");
        bw.newLine();
        // declare all nodes
        writeNodes(allNodes, bw);
        bw.newLine();
        // declare all edges
        writeRelationships(allNodes, bw);
        // end the graph
        bw.write("}");
        // close file
        bw.close();
    }

    /**
     * Populates the list of all nodes in a Gsn fragment
     *
     * @param allNodes
     * @param node
     */
    public static void getAllNodes(List<GsnNode> allNodes, GsnNode node) {

        // traverse the GSN fragment and add each node to the list
        if (node.getNodeType().equalsIgnoreCase("context")) {
            allNodes.add(node);
        } else if (node.getNodeType().equalsIgnoreCase("solution")) {
            allNodes.add(node);
            if (!(node.getInContextOf() == null)) {
                for (GsnNode subNode : node.getInContextOf()) {
                    getAllNodes(allNodes, subNode);
                }
            }
        } else if (node.getNodeType().equalsIgnoreCase("goal")) {
            allNodes.add(node);
            if (!(node.getInContextOf() == null)) {
                for (GsnNode subNode : node.getInContextOf()) {
                    getAllNodes(allNodes, subNode);
                }
            }
            if (!(node.getSupportedBy() == null)) {
                for (GsnNode subNode : node.getSupportedBy()) {
                    getAllNodes(allNodes, subNode);
                }
            }
        } else if (node.getNodeType().equalsIgnoreCase("strategy")) {
            allNodes.add(node);
            if (!(node.getInContextOf() == null)) {
                for (GsnNode subNode : node.getInContextOf()) {
                    getAllNodes(allNodes, subNode);
                }
            }
            if (!(node.getSupportedBy() == null)) {
                for (GsnNode subNode : node.getSupportedBy()) {
                    getAllNodes(allNodes, subNode);
                }
            }
        }
    }

    /**
     * Writes the node declarations to the dot file
     *
     * @param allNodes
     * @param bw
     * @throws IOException
     */
    public static void writeNodes(List<GsnNode> allNodes, BufferedWriter bw) throws IOException {
        bw.write("//Node declarations:-");
        bw.newLine();
        for (GsnNode node : allNodes) {
            String nodeShape = "";
            String nodeColor = "";
            String nodeText = "";
            String hoverDisplay = "No additional information is available";
            boolean nodeStatus = true;

            // deciding node shape and hovertext
            if (node.getNodeType().equalsIgnoreCase("context")) {
                nodeShape = "rectangle, style=\"rounded\" ";
                nodeText = node.getContext().getDisplayText();
                if (!(node.getContext().getExtraInfo() == null)) {
                    hoverDisplay = node.getContext().getExtraInfo();
                }
            } else if (node.getNodeType().equalsIgnoreCase("solution")) {
                nodeShape = "circle";
                nodeText = node.getSolution().getDisplayText();
                nodeStatus = node.getSolution().getStatus();
                if (!(node.getSolution().getExtraInfo() == null)) {
                    hoverDisplay = node.getSolution().getExtraInfo();
                }
            } else if (node.getNodeType().equalsIgnoreCase("goal")) {
                nodeShape = "box";
                nodeText = stringWrapper(node.getGoal().getDisplayText());
                nodeStatus = node.getGoal().getStatus();
            } else if (node.getNodeType().equalsIgnoreCase("strategy")) {
                nodeShape = "parallelogram";
                nodeText = node.getStrategy().getDisplayText();
                nodeStatus = node.getStrategy().getStatus();
            }

            // deciding node color
            if (nodeStatus) {
                nodeColor = "green";
            } else {
                nodeColor = "red";
            }
            if (node.getNodeType().equalsIgnoreCase("context")) {
                nodeColor = "black";
            }

            // writing string to declare node in dot file
            String nodeDeclareString =
                    node.getNodeId()
                            + " ["
                            + "tooltip=\""
                            + hoverDisplay
                            + "\", margin=0.05, style=bold, color="
                            + nodeColor
                            + ", shape="
                            + nodeShape
                            + ", label=\""
                            + node.getNodeId()
                            + "\\n\\n"
                            + nodeText
                            + "\"];";
            bw.write(nodeDeclareString);
            bw.newLine();
        }
    }

    /**
     * Declares all edges in the dot file
     *
     * @param allNodes
     * @param bw
     * @throws IOException
     */
    public static void writeRelationships(List<GsnNode> allNodes, BufferedWriter bw)
            throws IOException {
        bw.write("//Node relationships (edges):-");
        bw.newLine();
        for (GsnNode node : allNodes) {
            // supportedBy edges
            for (GsnNode support : node.getSupportedBy()) {
                bw.write(
                        node.getNodeId()
                                + " -> "
                                + support.getNodeId()
                                + " [splines=curved, weight=2, arrowsize=2.0]");
                bw.newLine();
            }
            // inContextOf edges
            for (GsnNode context : node.getInContextOf()) {
                bw.write(
                        node.getNodeId()
                                + " -> "
                                + context.getNodeId()
                                + " [splines=curved, arrowhead=empty, arrowsize=2.0]");
                bw.newLine();
            }
        }
    }

    /**
     * Takes a string and wraps it by adding a linebreak after every 50 characters Used to edit goal
     * statements which are taken "as is" from verdict
     *
     * @param original
     * @return
     */
    public static String stringWrapper(String original) {
        // normalize spaces in the string
        String normal = original.replaceAll("\\s{2,}", " ").trim();
        String wrapped =
                WordWrap.from(normal)
                        .maxWidth(50)
                        .insertHyphens(true) // true is the default
                        .wrap();
        return wrapped;
    }
}
