package at.dms.kjc.sir.statespace.transform;

import at.dms.kjc.sir.statespace.*;

/**
 * Exception that is thrown when we can't compute a transform for some reason.
 * This is a checked exception to ensure that the case of an impossible transformation is
 * explicity checked for and so that the compiler doesn't die (this optimization just
 * stops where it is).<br>
 *
 * $Id: NoTransformPossibleException.java,v 1.1 2009/02/24 18:15:24 hormati Exp $
 **/
public class NoTransformPossibleException extends Exception {
    public NoTransformPossibleException(String message) {
        super(message);
    }
}
