
package linker;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.EmittedArtifact;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.LinkerOrder.Order;

@LinkerOrder(Order.POST)
public class OfflineLinker extends AbstractLinker {

    @Override
    public String getDescription() {
        return "HTML 5 Offline Linker";
    }

    @Override
    public ArtifactSet link(TreeLogger logger, LinkerContext context, ArtifactSet artifacts) throws UnableToCompleteException {
        ArtifactSet artifactSet = new ArtifactSet(artifacts);
        StringBuilder buf = new StringBuilder();
        buf.append("CACHE MANIFEST\n" + //
                "../image.png\n" + //
                "../images.html\n" + //
                "../sync-24.gif\n" + //
                "../sync-complete-24.png\n" + //
                "../sync-disconnected-24.png\n" + //
                "examples.app.nocache.js\n");

        for (EmittedArtifact artifact : artifacts.find(EmittedArtifact.class))
            if (artifact.getPartialPath().endsWith("cache.js"))
                buf.append("").append(artifact.getPartialPath()).append("\n");

        buf.append("\n" + //
                "NETWORK:\n" + //
                "*\n");

        EmittedArtifact artifact = emitString(logger, buf.toString(), "cache.manifest");
        artifactSet.add(artifact);
        return artifactSet;
    }
}
