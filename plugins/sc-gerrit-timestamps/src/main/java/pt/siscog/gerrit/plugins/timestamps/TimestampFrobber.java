package pt.siscog.gerrit.plugins.timestamps;

import com.google.common.primitives.Bytes;
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
import org.eclipse.jgit.errors.LargeObjectException;
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
  private static final String ENCODING = "iso-8859-1";

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
        if (!entry.getNewMode().equals(FileMode.REGULAR_FILE)) {
          continue; // skip symlinks, submodules, executable files, etc.
        }

        ObjectId id = entry.getNewId().toObjectId();

        if (id.equals(ObjectId.zeroId())) {
          continue; // this file is no more, skip it.
        }

        byte[] data;
        try {
          data = reader.open(id).getCachedBytes(MAX_FILE_SIZE);
        } catch (LargeObjectException e) {
          continue; // skip large files.
        }

        if (Bytes.contains(data, (byte) 0)) {
          continue; // skip files that seem to be binary.
        }

        // TODO: avoid so many copies
        String content = new String(data, ENCODING);
        String newContent = content.replaceAll(TIMESTAMP_MARKER, timestamp);

        if (content.equals(newContent)) {
          continue; // skip unchanged files.
        }

        // TODO: avoid yet another copy
        final byte[] newData = newContent.getBytes(ENCODING);
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
