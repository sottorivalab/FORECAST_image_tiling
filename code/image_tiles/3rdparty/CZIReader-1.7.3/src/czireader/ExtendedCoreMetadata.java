/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package czireader;

import loci.formats.CoreMetadata;

/**
 *
 * @author adminntrahearn
 */

public class ExtendedCoreMetadata {
    public int xPosition;
    public int yPosition;
    
    public ExtendedCoreMetadata() {
        xPosition = 0;
        yPosition = 0;
    }
    
    public ExtendedCoreMetadata(ExtendedCoreMetadata c) {
        xPosition = c.xPosition;
        yPosition = c.yPosition;
    }
}
