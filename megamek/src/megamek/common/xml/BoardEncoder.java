/*
 * MegaMek - Copyright (C) 2003 Ben Mazur (bmazur@sev.org)
 * 
 *  This program is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU General Public License as published by the Free 
 *  Software Foundation; either version 2 of the License, or (at your option) 
 *  any later version.
 * 
 *  This program is distributed in the hope that it will be useful, but 
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 *  for more details.
 */

package megamek.common.xml;

import java.io.Writer;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import gd.xml.tiny.ParsedXML;
import megamek.common.*;
import megamek.common.util.StringUtil;

/**
 * Objects of this class can encode a <code>Board</code> object as XML
 * into an output writer and decode one from a parsed XML node.  It is used
 * when saving games into a version- neutral format.
 *
 * @author      James Damour <suvarov454@users.sourceforge.net>
 */
public class BoardEncoder {

    /**
     * Encode a <code>Board</code> object to an output writer.
     *
     * @param   board - the <code>Board</code> to be encoded.
     *          This value must not be <code>null</code>.
     * @param   out - the <code>Writer</code> that will receive the XML.
     *          This value must not be <code>null</code>.
     * @throws  <code>IllegalArgumentException</code> if the node is
     *          <code>null</code>.
     * @throws  <code>IOException</code> if there's any error on write.
     */
    public static void encode( Board board, Writer out )
        throws IOException
    {
        Enumeration iter; // used when marching through a list of sub-elements
        Coords coords;
        int x;
        int y;
        int turns;

        // First, validate our input.
        if ( null == board ) {
            throw new IllegalArgumentException( "The board is null." );
        }
        if ( null == out ) {
            throw new IllegalArgumentException( "The writer is null." );
        }

        // Start the XML stream for this board
        out.write( "<board version=\"1.0\" >" );

        // Write the hex array to the stream.
        out.write( "<boardData width=\"" );
        out.write( Integer.toString(board.width) );
        out.write( "\" height=\"" );
        out.write( Integer.toString(board.height) );
        out.write( "\" roadsAutoExit=\"" );
        out.write( board.getRoadsAutoExit() ? "true" : "false" );
        out.write( "\" >" );
        for ( y = 0; y < board.height; y++ ) {
            for ( x = 0; x < board.width; x++ ) {
                HexEncoder.encode( board.getHex(x,y), out );
            }
        }
        out.write( "</boardData>" );

        // Write out the buildings (if any).
        iter = board.getBuildings();
        if ( iter.hasMoreElements() ) {
            out.write( "<buildings>" );
            while ( iter.hasMoreElements() ) {
                BuildingEncoder.encode( (Building) iter.nextElement(), out );
            }
            out.write( "</buildings>" );
        }

        // Write out the infernos (if any).
        iter = board.getInfernoBurningCoords();
        if ( iter.hasMoreElements() ) {
            out.write( "<infernos>" );
            while ( iter.hasMoreElements() ) {
                // Encode the infernos as these coordinates.
                coords = (Coords) iter.nextElement();
                out.write( "<inferno>" );
                CoordsEncoder.encode( coords, out );
                turns = board.getInfernoIVBurnTurns( coords );
                // This value may be zero.
                if ( turns > 0 ) {
                    out.write( "<arrowiv turns=\"" );
                    out.write( Integer.toString(turns) );
                    out.write( "\" />" );
                }
                // -(Arrow IV turns - All Turns) = Standard Turns.
                turns -= board.getInfernoBurnTurns( coords );
                turns = -turns;
                if ( turns > 0 ) {
                    out.write( "<standard turns=\"" );
                    out.write( Integer.toString(turns) );
                    out.write( "\" />" );
                }
                out.write( "</inferno>" );
            }
            out.write( "</infernos>" );
        }

        // Finish the XML stream for this board.
        out.write( "</board>" );
    }

    /**
     * Decode a <code>Board</code> object from the passed node.
     *
     * @param   node - the <code>ParsedXML</code> node for this object.
     *          This value must not be <code>null</code>.
     * @param   game - the <code>Game</code> the decoded object belongs to.
     * @return  the <code>Board</code> object based on the node.
     * @throws  <code>IllegalArgumentException</code> if the node is
     *          <code>null</code>.
     * @throws  <code>IllegalStateException</code> if the node does not
     *          contain a valid <code>Board</code>.
     */
    public static Board decode( ParsedXML node, Game game ) {
        String attrStr = null;
        int attrVal = 0;
        Board retVal = null;
        Vector buildings = new Vector();
        Hashtable infernos = new Hashtable();
        int height = 0;
        int width = 0;
        Hex[] hexes = null;
        boolean roadsAutoExit = false;
        Enumeration subnodes = null;
        ParsedXML subnode = null;

        // Did we get a null node?
        if ( null == node ) {
            throw new IllegalArgumentException( "The board is null." );
        }

        // Make sure that the node is for a Board object.
        if ( !node.getName().equals( "board" ) ) {
            throw new IllegalStateException( "Not passed a board node." );
        }

        // TODO : perform version checking.

        // Walk the board node's children.
        Enumeration children = node.elements();
        while ( children.hasMoreElements() ) {
            ParsedXML child = (ParsedXML) children.nextElement();
            String childName = child.getName();

            // Handle null child names.
            if ( null == childName ) {

                // No-op.
            }

            // Did we find the boardData node?
            else if ( childName.equals( "boardData" ) ) {

                // There should be only one boardData node.
                if ( null != retVal ) {
                    throw new IllegalStateException
                        ( "More than one 'boardData' node in a board node." );
                }

                // Get the number of boardData.
                attrStr = child.getAttribute( "height" );
                if ( null == attrStr ) {
                    throw new IllegalStateException
                        ( "Couldn't decode the boardData for a board node." );
                }

                // Try to pull the height from the attribute string
                try {
                    attrVal = Integer.parseInt( attrStr );
                }
                catch ( NumberFormatException exp ) {
                    throw new IllegalStateException
                        ( "Couldn't get an integer from " + attrStr );
                }

                // Do we have a valid value?
                if ( height < 0 || height > Board.BOARD_MAX_HEIGHT ) {
                    throw new IllegalStateException
                        ( "Illegal value for height: " + attrStr );
                }
                height = attrVal;

                // Get the number of boardData.
                attrStr = child.getAttribute( "width" );
                if ( null == attrStr ) {
                    throw new IllegalStateException
                        ( "Couldn't decode the boardData for a board node." );
                }

                // Try to pull the width from the attribute string
                try {
                    attrVal = Integer.parseInt( attrStr );
                }
                catch ( NumberFormatException exp ) {
                    throw new IllegalStateException
                        ( "Couldn't get an integer from " + attrStr );
                }

                // Do we have a valid value?
                if ( width < 0 || width > Board.BOARD_MAX_WIDTH ) {
                    throw new IllegalStateException
                        ( "Illegal value for width: " + attrStr );
                }
                width = attrVal;

                // Read the "roadsAutoExit" attribute.
                roadsAutoExit = StringUtil.parseBoolean
                    ( child.getAttribute("roadsAutoExit") );

                // Create an array to hold all the boardData.
                hexes = new Hex[height * width];

                // Walk through the subnodes, parsing out hex nodes.
                int numHexes = 0;
                subnodes = child.elements();
                while ( subnodes.hasMoreElements() ) {

                    // Is this a "hex" node?
                    subnode = (ParsedXML) subnodes.nextElement();
                    if ( subnode.getName().equals( "hex" ) ) {

                        // Are there too many hex nodes?
                        if ( hexes.length == numHexes ) {
                            throw new IllegalStateException
                                ( "Too many hexes in a board node." );
                        }

                        // Parse out this hex node.
                        hexes[numHexes] = HexEncoder.decode( subnode, game );

                        // Increment the number of boardData.
                        numHexes++;

                    } // End found-"hex"-node

                } // Look at the next subnode.

                // Have we found enough hex nodes?
                if ( numHexes < hexes.length ) {
                    throw new IllegalStateException
                        ( "Not enough hexes in a board node." );
                }

            } // End found-"boardData"-node

            // Did we find the infernos node?
            else if ( childName.equals("infernos") ) {
                subnodes = child.elements();
                while ( subnodes.hasMoreElements() ) {
                    subnode = (ParsedXML) subnodes.nextElement();
                    if ( subnode.getName().equals("inferno") ) {
                        Coords coords = null;
                        InfernoTracker tracker = new InfernoTracker();

                        // Try to find the inferno detail nodes.
                        Enumeration details = subnode.elements();
                        while ( details.hasMoreElements() ) {
                            ParsedXML detail = (ParsedXML) 
                                details.nextElement();

                            // Have we found the coords?
                            if ( detail.getName().equals("coords") ) {
                                coords = CoordsEncoder.decode
                                    ( detail, game );
                            }

                            // Have we found the Arrow IV inferno entry?
                            if ( detail.getName().equals("coords") ) {
                                coords = CoordsEncoder.decode
                                    ( detail, game );
                            }

                            // Have we found the standard inferno entry?
                            if ( detail.getName().equals("coords") ) {
                                coords = CoordsEncoder.decode
                                    ( detail, game );
                            }

                        } // Handle the next detail node.

                        // We *did* find the coords, right?

                        // Add this inferno tracker to the map.

                    } // End found-"inferno"-subnode

                } // Check the next subnode

            } // End found-"infernos"-child

            // Did we find the buildings node?
            else if ( childName.equals("buildings") ) {
                subnodes = child.elements();
                while ( subnodes.hasMoreElements() ) {
                    subnode = (ParsedXML) subnodes.nextElement();
                    if ( subnode.getName().equals("building") ) {
                        buildings.addElement
                            ( BuildingEncoder.decode( subnode, game ) );
                    }
                } // Handle the next building
            }

        } // Look at the next child.

        // Did we find all needed child nodes?
        if ( null == hexes ) {
            throw new IllegalStateException
                ( "Couldn't locate the boardData for a board node." );
        }

        // Construct the board.
        retVal = new Board( width, height, hexes, buildings, infernos );

        // Return the board for this node.
        return retVal;
    }

}

