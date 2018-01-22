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
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
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
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

@Listen
@Singleton
public class TimestampFrobber implements CommitModifier {
  // TODO (MAYBE): turn these constants into config options
  private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MiB
  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("\tyyyy/MM/dd\t");
  private static final String TIMESTAMP_MARKER = "\t0000/00/00\t";

  @Inject
  public TimestampFrobber() {}

  private List<DiffEntry> getChangedFiles(ObjectReader reader, RevTree oldTree, RevTree newTree)
      throws IOException {
    DiffFormatter diffFmt = new DiffFormatter(NullOutputStream.INSTANCE);
    diffFmt.setReader(reader, new Config());
    return diffFmt.scan(oldTree, newTree);
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

      String timestamp = TIMESTAMP_FORMAT.format(new Date());

      for (DiffEntry entry : getChangedFiles(reader, mergeTip.getTree(), rw.parseTree(newTree))) {
        ObjectId id = entry.getNewId().toObjectId();

        // TODO: skip file if it's too big
        // TODO: skip file if it looks binary
        byte[] data = reader.open(id).getCachedBytes(MAX_FILE_SIZE);
        //reader.open(id).openStream().getSize();

        // TODO: avoid reading the whole file into memory.
        String content = new String(data, "iso-8859-1");

        String newContent = content.replaceAll(TIMESTAMP_MARKER, timestamp);

        // TODO: avoid yet another array
        final byte[] newData = newContent.getBytes("iso-8859-1");
        final ByteArrayInputStream newBAIS = new ByteArrayInputStream(newData);

        PathEdit edit = new PathEdit(entry.getNewPath()) {
          @Override
          public void apply(DirCacheEntry dirCacheEntry) {
            if (dirCacheEntry.getFileMode() != FileMode.GITLINK) {
              try {
                ObjectId newBlobObjectId = inserter.insert(OBJ_BLOB, newData.length, newBAIS);
                if (dirCacheEntry.getRawMode() == 0) {
                  dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
                }
                dirCacheEntry.setObjectId(newBlobObjectId);
              } catch (IOException e) {
                e.printStackTrace(); // FIXME: is this the proper way to log this?
              }
            }
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
