import io.github.jwharm.javagi.base.GErrorException;
import io.github.jwharm.javagi.base.Out;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gtk.annotations.GtkCallback;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import io.github.jwharm.javagi.gtk.types.Types;
import org.gnome.gio.File;
import org.gnome.gio.MenuModel;
import org.gnome.gio.SettingsBindFlags;
import org.gnome.glib.Type;
import org.gnome.gobject.BindingFlags;
import org.gnome.gobject.GObject;
import org.gnome.gtk.*;
import org.gnome.gio.Settings;

import java.lang.foreign.MemorySegment;
import java.util.HashSet;
import java.util.Set;

@GtkTemplate(ui="/org/gtk/exampleapp/window.ui")
public class ExampleAppWindow extends ApplicationWindow {

  private static final Type gtype = Types.register(ExampleAppWindow.class);

  @GtkChild public Stack stack;
  @GtkChild public MenuButton gears;
  @GtkChild public ToggleButton search;
  @GtkChild public SearchBar searchbar;
  @GtkChild public SearchEntry searchentry;
  @GtkChild public Revealer sidebar;
  @GtkChild public ListBox words;

  private Settings settings;

  public static Type getType() {
    return gtype;
  }

  public ExampleAppWindow(MemorySegment address) {
    super(address);
  }

  public static ExampleAppWindow create(ExampleApp app) {
    return GObject.newInstance(getType(), "application", app, null);
  }

  @InstanceInit
  public void init() {
    var builder = GtkBuilder.fromResource("/org/gtk/exampleapp/gears-menu.ui");
    var menu = (MenuModel) builder.getObject("menu");
    gears.setMenuModel(menu);

    settings = new Settings("org.gtk.exampleapp");
    settings.bind("transition", stack, "transition-type", SettingsBindFlags.DEFAULT);
    search.bindProperty("active", searchbar, "search-mode-enabled", BindingFlags.BIDIRECTIONAL);

    settings.bind("show-words", sidebar, "reveal-child", SettingsBindFlags.DEFAULT);
    sidebar.onNotify("reveal-child", _ -> updateWords());
    addAction(settings.createAction("show-words"));
  }

  public void open(File file) {
    String basename = file.getBasename();

    var scrolled = new ScrolledWindow();
    scrolled.setHexpand(true);
    scrolled.setVexpand(true);
    var view = new TextView();
    view.setEditable(false);
    view.setCursorVisible(false);
    scrolled.setChild(view);
    stack.addTitled(scrolled, basename, basename);
    var buffer = view.getBuffer();

    try {
      var contents = new Out<byte[]>();
      if (file.loadContents(null, contents, null)) {
        String str = new String(contents.get());
        buffer.setText(str, str.length());
      }
    } catch (GErrorException e) {
      throw new RuntimeException(e);
    }

    var tag = buffer.createTag(null, null);
    settings.bind("font", tag, "font", SettingsBindFlags.DEFAULT);
    TextIter startIter = new TextIter();
    TextIter endIter = new TextIter();
    buffer.getStartIter(startIter);
    buffer.getEndIter(endIter);
    buffer.applyTag(tag, startIter, endIter);

    search.setSensitive(true);

    updateWords();
  }

  @GtkCallback(name="search_text_changed")
  public void searchTextChanged() {
    String text = searchentry.getText();

    if (text.isEmpty())
      return;

    var tab = (ScrolledWindow) stack.getVisibleChild();
    var view = (TextView) tab.getChild();
    var buffer = view.getBuffer();

    // Very simple-minded search implementation
    TextIter startIter = new TextIter();
    TextIter matchStart = new TextIter();
    TextIter matchEnd = new TextIter();
    buffer.getStartIter(startIter);
    if (startIter.forwardSearch(text, TextSearchFlags.CASE_INSENSITIVE,
                                matchStart, matchEnd, null)) {
      buffer.selectRange(matchStart, matchEnd);
      view.scrollToIter(matchStart, 0.0, false, 0.0, 0.0);
    }
  }

  @GtkCallback(name="visible_child_changed")
  public void visibleChildChanged() {
    if (stack.inDestruction())
      return;

    searchbar.setSearchMode(false);
    updateWords();
  }

  private void findWord(Button button) {
    String word = button.getLabel();
    searchentry.setText(word);
  }

  private void updateWords() {
    var tab = (ScrolledWindow) stack.getVisibleChild();
    if (tab == null)
      return;

    var view = (TextView) tab.getChild();
    var buffer = view.getBuffer();

    Set<String> strings = new HashSet<>();

    TextIter end, start = new TextIter();
    buffer.getStartIter(start);

    outer:
    while (!start.isEnd()) {
      while (!start.startsWord())
        if (!start.forwardChar())
          break outer;

      end = start.copy();
      if (!end.forwardWordEnd())
        break;

      var word = buffer.getText(start, end, false);
      strings.add(word.toLowerCase());
      start = end.copy();
    }

    Widget child;
    while ((child = words.getFirstChild()) != null)
      words.remove(child);

    for (var key : strings) {
      var row = Button.withLabel(key);
      row.onClicked(() -> findWord(row));
      words.insert(row, -1);
    }
  }
}
