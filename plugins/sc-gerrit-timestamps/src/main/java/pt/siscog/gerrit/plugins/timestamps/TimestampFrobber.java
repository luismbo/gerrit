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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

@Listen
@Singleton
public class TimestampFrobber implements CommitModifier {
  // TODO (MAYBE): turn these constants into config options
  private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MiB
  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("$1yyyy/MM/dd$2");
  private static final Pattern TIMESTAMP_MARKER = Pattern.compile("(\\s)0000/00/00(\\s)");
  private static final String ENCODING = "iso-8859-1";

  @Inject
  public TimestampFrobber() {}

  private List<DiffEntry> getChangedFiles(ObjectReader reader, RevTree oldTree, RevTree newTree)
      throws IOException {
    DiffFormatter diffFmt = new DiffFormatter(NullOutputStream.INSTANCE);
    diffFmt.setReader(reader, new Config());
    return diffFmt.scan(oldTree, newTree);
  }

  private static final Pattern patchVersionPattern = Pattern.compile("patch-.*-(\\d+)\\.lisp");
  private static OptionalInt getPatchVersion(String path) {
    if (path != null) {
      Matcher m = patchVersionPattern.matcher(path);

      if (m.find()) {
        try {
          return OptionalInt.of(Integer.parseInt(path.substring(m.start(1), m.end(1))));
        } catch (NumberFormatException e) {
          return OptionalInt.empty();
        }
      }
    }

    return OptionalInt.empty();
  }

  private static Optional<String> setPatchVersion(String path, int newVersion) {
    Matcher m = patchVersionPattern.matcher(path);

    if (m.find()) {
      return Optional.of(path.substring(0, m.start(1)) + newVersion + path.substring(m.end(1), path.length()));
    }

    return Optional.empty();
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
        String newContent = TIMESTAMP_MARKER.matcher(content).replaceAll(timestamp);


        if (content.equals(newContent)) {
          // skip files without timestamp markers. Notably, this mean we won't
          // bump patch file versions for such files.
          continue;
        }

        // TODO: avoid yet another copy
        final byte[] newData = newContent.getBytes(ENCODING);
        final ObjectId newBlobObjectId = inserter.insert(OBJ_BLOB, newData);

        PathEdit edit = new PathEdit(entry.getNewPath()) {
          @Override
          public void apply(DirCacheEntry dirCacheEntry) {
            if (dirCacheEntry.getFileMode() != FileMode.GITLINK) {
              if (dirCacheEntry.getRawMode() == 0) {
                dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
              }
              dirCacheEntry.setObjectId(newBlobObjectId);
            }
          }
        };

        DirCacheEditor dirCacheEditor = dirCache.editor();
        dirCacheEditor.add(edit);
        dirCacheEditor.finish();

        dirCacheEditor = dirCache.editor();

        // If both new and old patches have a version and the new version
        // is not greater than the old version, we force the new patch's
        // version to be oldVersion+1. (Setting the version on a versionless
        // patch would complicate setPatchVersion() and it's not clear why
        // the new patch wouldn't have a version.)
        //
        // Note: if the patch doesn't contain at least one timestamp marker,
        // we don't get this far. That way we can tweak the initial patches
        // without having their versions inadvertently bumped.
        OptionalInt newVersion = getPatchVersion(entry.getNewPath());
        OptionalInt oldVersion = getPatchVersion(entry.getOldPath());

        if (oldVersion.isPresent() && newVersion.isPresent()) {
          int finalVersion = Math.max(oldVersion.getAsInt()+1, newVersion.getAsInt());
          Optional<String> newNewPath = setPatchVersion(entry.getNewPath(), finalVersion);
          if (newNewPath.isPresent()) {
            DirCacheEditor.DeletePath deletePathEdit = new DirCacheEditor.DeletePath(entry.getNewPath());
            AddPath addPathEdit = new AddPath(newNewPath.get(), entry.getNewMode(), newBlobObjectId);
            dirCacheEditor.add(deletePathEdit);
            dirCacheEditor.add(addPathEdit);
          }
        }

        dirCacheEditor.finish();
      }

      ObjectId finalTree = dirCache.writeTree(inserter);
      inserter.flush();
      return finalTree;
    }
  }

  private class AddPath extends DirCacheEditor.PathEdit {

    private final FileMode fileMode;
    private final ObjectId objectId;

    AddPath(String filePath, FileMode fileMode, ObjectId objectId) {
      super(filePath);
      this.fileMode = fileMode;
      this.objectId = objectId;
    }

    @Override
    public void apply(DirCacheEntry dirCacheEntry) {
      dirCacheEntry.setFileMode(fileMode);
      dirCacheEntry.setObjectId(objectId);
    }
  }

}
