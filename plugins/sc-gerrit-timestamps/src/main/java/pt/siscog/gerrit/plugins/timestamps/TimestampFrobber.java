package pt.siscog.gerrit.plugins.timestamps;

import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.git.CommitModifier;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

@Listen
@Singleton
public class TimestampFrobber implements CommitModifier {
  @Inject
  public TimestampFrobber() {
  }

  @Override
  public ObjectId onSubmit(ObjectInserter inserter, RevWalk rw, RevCommit mergeTip,
                           ObjectId newTree, String newCommitMessage)
    throws IOException {

    try (ObjectReader reader = inserter.newReader()) {
      DirCache dirCache = DirCache.newInCore();
      DirCacheBuilder dirCacheBuilder = dirCache.builder();
      dirCacheBuilder.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, newTree);
      dirCacheBuilder.finish();

      RevTree cTree = rw.parseTree(newTree);
      DiffFormatter diffFmt = new DiffFormatter(NullOutputStream.INSTANCE);
      diffFmt.setReader(reader, new Config());
      List<DiffEntry> result = diffFmt.scan(mergeTip.getTree(), cTree);

      for (DiffEntry entry : result) {
        ObjectId id = entry.getNewId().toObjectId();

        // TODO: skip file if it's too big
        // TODO: skip file if it looks binary
        byte[] data = reader.open(id).getBytes();

        // TODO: avoid reading the whole file into memory.
        String content = new String(data, "iso-8859-1");

        // TODO (MAYBE): grab magic date from config file
        // TODO: compute current date in... UTC or something
        // TODO: check for TAB before "00/00/00"
        String newContent = content.replaceAll("00/00/00", "2018/01/19");

        // TODO: avoid yet another array
        final byte[] newData = newContent.getBytes("iso-8859-1");
        final ByteArrayInputStream newBAIS = new ByteArrayInputStream(newData);

        // TODO: refactor this out.
        DirCacheEditor.PathEdit edit = new DirCacheEditor.PathEdit(entry.getNewPath()) {
          @Override
          public void apply(DirCacheEntry dirCacheEntry) {
            try {
              if (dirCacheEntry.getFileMode() == FileMode.GITLINK) {
                dirCacheEntry.setLength(0);
                dirCacheEntry.setLastModified(0);
                ObjectId newObjectId = ObjectId.fromString(newData, 0);
                dirCacheEntry.setObjectId(newObjectId);
              } else {
                if (dirCacheEntry.getRawMode() == 0) {
                  dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
                }
                ObjectId newBlobObjectId = createNewBlobAndGetItsId();
                dirCacheEntry.setObjectId(newBlobObjectId);
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          }

          private ObjectId createNewBlobAndGetItsId() throws IOException {
            return inserter.insert(OBJ_BLOB, newData.length, newBAIS);
          }
        };

        // TODO: detect whether there was any change at all
        DirCacheEditor dirCacheEditor = dirCache.editor();
        dirCacheEditor.add(edit);
        dirCacheEditor.finish();
      }

      ObjectId finalTree = dirCache.writeTree(inserter);
      inserter.flush();
      return finalTree;
    }
  }
}
