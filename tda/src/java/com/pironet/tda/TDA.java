/**
 * Thread Dump Analysis Tool, parses Thread Dump input and displays it as tree
 *
 * This file is part of TDA - Thread Dump Analysis Tool.
 *
 * TDA is free software; you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * TDA is distributed in the hope that it will be useful,h
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * TDA should have received a copy of the Lesser GNU General Public License
 * along with Foobar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: TDA.java,v 1.93 2007-10-03 12:50:27 irockel Exp $
 */
package com.pironet.tda;

import com.pironet.tda.utils.AppInfo;
import com.pironet.tda.utils.Browser;
import com.pironet.tda.utils.HistogramTableModel;
import com.pironet.tda.utils.PrefManager;
import com.pironet.tda.utils.StatusBar;
import com.pironet.tda.utils.SwingWorker;
import com.pironet.tda.utils.TableSorter;
import com.pironet.tda.utils.TreeRenderer;
import com.pironet.tda.utils.jedit.JEditTextArea;
import com.pironet.tda.utils.jedit.PopupMenu;
import java.awt.BorderLayout;
import java.io.FileNotFoundException;
import java.util.Enumeration;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import javax.swing.JTree;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.Position;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.io.IOException;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ProgressMonitorInputStream;
import javax.swing.UIManager;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreePath;

/**
 * main class of the Thread Dump Analyzer. Start using static main method.
 *
 * @author irockel
 */
public class TDA extends JPanel implements TreeSelectionListener, ActionListener, MenuListener {
    private static JFileChooser fc;
    private static boolean DEBUG = false;
    private static int DIVIDER_SIZE = 4;
    protected static JFrame frame;
    
    private static String dumpFile;
    
    private static String loggcFile;
    
    private static int fontSizeModifier = 0;

    
    private static TDA myTDA = null;
    
    private JEditorPane htmlPane;
    private JEditTextArea jeditPane;
    private JTree tree;
    private JSplitPane splitPane;
    private JSplitPane topSplitPane;
    private TreePath mergeDump;
    private DumpStore dumpStore;
    private Vector topNodes;
    private InputStream dumpFileStream;
    private JScrollPane htmlView;
    private JScrollPane tableView;
    private JScrollPane dumpView;
    private JTextField filter;
    private JCheckBox checkCase;
    private PreferencesDialog prefsDialog;
    private FilterDialog filterDialog;
    private LongThreadDialog longThreadDialog;
    private JMXConnectDialog jmxConnectionDialog;
    private JTable histogramTable;
    private JMenuItem showDumpMenuItem;
    
    private static Font HTML_DEFAULT_FONT = new Font("SansSerif", Font.PLAIN, 10);
    private static Font PLAIN_DEFAULT_FONT = new Font("Courier", Font.PLAIN, 11);
    
    private StatusBar statusBar;
    
    private SearchDialog searchDialog;
    
    /**
     * singleton access method for TDA
     */
    public static TDA get() {
        if(myTDA == null) {
            myTDA = new TDA();
        }
        
        return(myTDA);
    }
    
    /**
     * private constructor for singleton TDA
     */
    private TDA() {
        super(new BorderLayout());
        
        // init L&F
        setupLookAndFeel();
        
        init();
    }
    
    private void init() {
        // init everything
        tree = new JTree();
        
        //Create the HTML viewing pane.
        htmlPane = new JEditorPane("text/html", getInfoText());
        htmlPane.setEditable(false);
        
        JEditorPane emptyPane = new JEditorPane("text/html", "");
        emptyPane.setEditable(false);
        
        htmlPane.addHyperlinkListener(
                new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent evt) {
                // if a link was clicked
                if(evt.getEventType()==HyperlinkEvent.EventType.ACTIVATED) {
                    if(evt.getDescription().startsWith("monitor")) {
                        navigateToMonitor(evt.getDescription());
                    } else if(evt.getDescription().startsWith("dump")) {
                        navigateToDump();
                    } else if(evt.getDescription().startsWith("wait")) {
                        navigateToChild("Threads waiting");
                    } else if(evt.getDescription().startsWith("sleep")) {
                        navigateToChild("Threads sleeping");
                    } else if(evt.getDescription().startsWith("dead")) {
                        navigateToChild("Deadlocks");
                    } else if(evt.getURL() != null) {
                        try {
                            // launch a browser with the appropriate URL
                            Browser.open(evt.getURL().toString());
                        } catch(InterruptedException e) {
                            System.out.println("Error launching external browser.");
                        } catch(IOException e) {
                            System.out.println("I/O error launching external browser." + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        
        htmlView = new JScrollPane(htmlPane);
        JScrollPane emptyView = new JScrollPane(emptyPane);
        
        // create the top split pane
        topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        topSplitPane.setLeftComponent(emptyView);
        topSplitPane.setDividerSize(DIVIDER_SIZE);
        
        //Add the scroll panes to a split pane.
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setBottomComponent(htmlView);
        splitPane.setTopComponent(topSplitPane);
        splitPane.setDividerSize(DIVIDER_SIZE);
        
        Dimension minimumSize = new Dimension(200, 50);
        htmlView.setMinimumSize(minimumSize);
        emptyView.setMinimumSize(minimumSize);
        
        //Add the split pane to this panel.
        add(htmlView, BorderLayout.CENTER);
        statusBar = new StatusBar();
        add(statusBar, BorderLayout.SOUTH);
        
        firstFile = true;
        setFileOpen(false);
        
    }
    
    /**
     * tries the native look and feel on mac and windows and metal on unix (gtk still
     * isn't looking that nice, even in 1.6)
     */
    private void setupLookAndFeel() {
        try {
            //--- set the desired preconfigured plaf ---
            UIManager.LookAndFeelInfo currentLAFI = null;
            
            // retrieve plaf param.
            String plaf = "Mac,Windows,Metal";
            if(PrefManager.get().isUseGTKLF()) {
                plaf = "GTK,Mac,Windows,Metal";
            }
            
            // this line needs to be implemented in order to make L&F work properly
            UIManager.getLookAndFeelDefaults().put("ClassLoader", getClass().getClassLoader());
            
            // query list of L&Fs
            UIManager.LookAndFeelInfo[] plafs = UIManager.getInstalledLookAndFeels();
            
            if ((plaf != null) && (!"".equals(plaf))) {
                
                String[] instPlafs = plaf.split(",");
                search:
                    for(int i = 0; i < instPlafs.length; i++) {
                    for(int j=0; j<plafs.length; j++) {
                        currentLAFI = plafs[j];
                        if(currentLAFI.getName().startsWith(instPlafs[i])) {
                            UIManager.setLookAndFeel(currentLAFI.getClassName());
                            // setup font
                            setUIFont(new FontUIResource("SansSerif",Font.PLAIN,11));
                            break search;
                        }
                    }
                    }
            }
            
            if(plaf.startsWith("GTK")) {
                setFontSizeModifier(2);
            }
        } catch (Exception except) {
            // setup font
            setUIFont(new FontUIResource("SansSerif",Font.PLAIN,11));
        }
    }
    
    private String getInfoText() {
        StringBuffer info = new StringBuffer("<html><body bgcolor=\"ffffff\"><font face=\"System\" size=\""+ getFontSizeModifier(1) + "\"><b>");
        info.append("<img border=0 src=\"" + TDA.class.getResource("icons/TDA.gif") + "\">  ");
        info.append(AppInfo.getAppInfo());
        info.append("</b></font><hr><font face=\"System\"><p>");
        info.append("(C)opyright ");
        info.append(AppInfo.getCopyright());
        info.append(" - Ingo Rockel<br>");
        info.append("Version: <b>");
        info.append(AppInfo.getVersion());
        info.append("</b><p>");
        info.append("Select File/Open to open your log file with thread dumps to start analyzing these thread dumps.<p>See Help/Overview for information on how to obtain a thread dump from your VM.</p></font></body></html>");
        return(info.toString());
    }
    
    public void initDumpDisplay() {
        // clear tree
        dumpStore = new DumpStore();
        
        topNodes = new Vector();
        getMainMenu().getLongMenuItem().setEnabled(true);
        getMainMenu().getCloseMenuItem().setEnabled(true);
        getMainMenu().getCloseAllMenuItem().setEnabled(true);
        
        addDumpFile();
        if(isFileOpen()) {
            if(topSplitPane.getDividerLocation() <= 0) {
                topSplitPane.setDividerLocation(200);
            }
            
            // change from html view to split pane
            remove(0);
            revalidate();
            htmlPane.setText("");
            splitPane.setBottomComponent(htmlView);
            add(splitPane, BorderLayout.CENTER);
            if(PrefManager.get().getDividerPos() > 0) {
                splitPane.setDividerLocation(PrefManager.get().getDividerPos());
            } else {
                // set default divider location
                splitPane.setDividerLocation(100);
            }
            revalidate();
        }
    }

    /**
     * add the set dumpFileStream to the tree
     */
    private void addDumpFile() {
        String[] file = new String[1];
        file[0] = dumpFile;
        addDumpFiles(file);
    }

    private boolean isLogfileSizeOk(String fileName) {
        File logFile = new File(fileName);
        return(logFile.isFile() && ((PrefManager.get().getMaxLogfileSize() == 0) || 
                (logFile.length() <= (PrefManager.get().getMaxLogfileSize() * 1024))));
    }
    
    /**
     * sync object is needed to synchronize opening of multiple files.
     */
    private static Object syncObject = new Object();
    
    /**
     * add the set dumpFileStream to the tree
     */
    private void addDumpFiles(String[] files) {
        for(int i = 0; i < files.length; i++) {
            try {
                final InputStream dumpFileStream = new ProgressMonitorInputStream(
                        this, "Parsing " + files[i], new FileInputStream(files[i]));
            
                //Create the nodes.
                final DefaultMutableTreeNode top = new DefaultMutableTreeNode(new Logfile(files[i]));
                topNodes.add(top);
                if(isLogfileSizeOk(files[i])) {
                    DefaultMutableTreeNode logFile = new DefaultMutableTreeNode(new LogFileContent(files[i]));                    
                    top.add(logFile);
                }
                setFileOpen(true);
                
                final SwingWorker worker = new SwingWorker() {
                    
                    public Object construct() {
                        synchronized(syncObject) {
                            int divider = splitPane.getDividerLocation();
                            addThreadDumps(top, dumpFileStream);
                            createTree();
                            tree.expandRow(1);
                            
                            splitPane.setDividerLocation(divider);
                        }
                        
                        return null;
                    }
                    
                };
                worker.start();
            } catch (FileNotFoundException ex) {
                JOptionPane.showMessageDialog(this.getRootPane(),
                        "Error opening "  + ex.getMessage() + ".",
                        "Error opening file", JOptionPane.ERROR_MESSAGE);
            }
        }        
    }
        
    protected void createTree() {
        //Create a tree that allows multiple selection at a time.
        if(topNodes.size() == 1) {
            tree = new JTree((DefaultMutableTreeNode) topNodes.get(0));
            frame.setTitle("TDA - Thread Dumps of " + dumpFile);
        } else {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Thread Dump Nodes");
            for(int i = 0; i < topNodes.size(); i++) {
                root.add((DefaultMutableTreeNode) topNodes.get(i));
            }
            tree = new JTree(root);
            tree.setRootVisible(false);
            frame.setTitle(frame.getTitle() + " ...");
        }
        
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode
                (TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        
        tree.setCellRenderer(new TreeRenderer());
        
        //Create the scroll pane and add the tree to it.
        JScrollPane treeView = new JScrollPane(tree);
        
        topSplitPane.setLeftComponent(treeView);
        
        Dimension minimumSize = new Dimension(200, 50);
        treeView.setMinimumSize(minimumSize);
        
        //Listen for when the selection changes.
        tree.addTreeSelectionListener(this);
        
        createPopupMenu();
        
    }

    private boolean threadDisplay = false;
    
    private void setThreadDisplay(boolean value) {
        threadDisplay = value;
        if(!value) {
            // clear thread pane
            topSplitPane.setRightComponent(null);
        }
    }
    
    private boolean isThreadDisplay() {
        return(threadDisplay);
    }
    
    /**
     * Required by TreeSelectionListener interface.
     */
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)
        e.getPath().getLastPathComponent();
        
        if (node == null) {
            return;
        }
        
        Object nodeInfo = node.getUserObject();
        if (nodeInfo instanceof ThreadInfo) {
            displayThreadInfo(nodeInfo);
            setThreadDisplay(true);
        } else if (nodeInfo instanceof HistogramInfo) {
            HistogramInfo tdi = (HistogramInfo)nodeInfo;
            displayTable((HistogramTableModel) tdi.content);
            setThreadDisplay(false);
        } else if(nodeInfo instanceof LogFileContent) {
            displayLogFileContent(nodeInfo);
        } else if (nodeInfo instanceof Logfile && ((String)((Logfile)nodeInfo).getContent()).startsWith("Thread Dumps")) {
            displayLogFile();
            setThreadDisplay(false);
        } else if (nodeInfo instanceof Category) {
            displayCategory(nodeInfo);
            setThreadDisplay(true);
        } else {
            setThreadDisplay(false);
            displayContent(null);
        }
    }
    
    private void displayThreadInfo(Object nodeInfo) {
        ThreadInfo ti = (ThreadInfo)nodeInfo;
        if(ti.info != null) {
            StringBuffer sb = new StringBuffer(ti.info);
            sb.append(ti.content);
            displayContent(sb.toString());
        } else {
            displayContent(ti.content);
        }
    }
    
    private void displayLogFile() {
        if(splitPane.getBottomComponent() != htmlView) {
            splitPane.setBottomComponent(htmlView);
        }
        htmlPane.setContentType("text/html");
        htmlPane.setFont(HTML_DEFAULT_FONT);
        htmlPane.setText("");
        htmlPane.setCaretPosition(0);
        topSplitPane.setRightComponent(null);
    }
    
    private void displayLogFileContent(Object nodeInfo) {
        int dividerLocation = splitPane.getDividerLocation();
        if(splitPane.getBottomComponent() != jeditPane) {
            if(jeditPane == null) {
                initJeditView();
            }
            splitPane.setBottomComponent(jeditPane);
        }
        
        LogFileContent lfc = (LogFileContent) nodeInfo;
        jeditPane.setText(lfc.getContent());
        jeditPane.setCaretPosition(0);
        splitPane.setDividerLocation(dividerLocation);
        
    }
    
    /**
     * initialize the base components needed for the jedit view of the
     * log file
     */
    private void initJeditView() {
        jeditPane = new JEditTextArea();
        jeditPane.setEditable(false);
        jeditPane.setCaretVisible(false);
        jeditPane.setCaretBlinkEnabled(false);
        jeditPane.setRightClickPopup(new PopupMenu());
    }
    
    /**
     * display selected category in upper right frame
     */
    private void displayCategory(Object nodeInfo) {
        Category cat = ((Category) nodeInfo);
        Dimension size = null;
        ((JScrollPane) topSplitPane.getLeftComponent()).setPreferredSize(topSplitPane.getLeftComponent().getSize());
        boolean needDividerPos = false;
        
        if(topSplitPane.getRightComponent() != null) {
            size = topSplitPane.getRightComponent().getSize();
        } else {
            needDividerPos = true;
        }
        if(cat.getLastView() == null) {
            JTree catTree = cat.getCatTree(this);
            catTree.addMouseListener(getCatPopupMenu());
            dumpView = new JScrollPane(catTree);
            if(size != null) {
                dumpView.setPreferredSize(size);
            }
            
            topSplitPane.setRightComponent(dumpView);
            cat.setLastView(dumpView);
        } else {
            if(size != null) {
                cat.getLastView().setPreferredSize(size);
            }
            topSplitPane.setRightComponent(cat.getLastView());
        }
        if(cat.getCatTree(this).getSelectionPath() != null) {
            displayThreadInfo(((DefaultMutableTreeNode) cat.getCatTree(this).getSelectionPath().getLastPathComponent()).getUserObject());
        } else {
            displayContent(null);
        }
        if(needDividerPos) {
            topSplitPane.setDividerLocation(PrefManager.get().getTopDividerPos());
        }
        if(cat.howManyFiltered() > 0) {
            statusBar.setInfoText("Filtered " + cat.howManyFiltered() + " elements in this category. Showing remaining " + cat.showing() + " elements.");
        } else {
            statusBar.setInfoText(AppInfo.getStatusBarInfo());
        }
    }
    
    private void displayContent(String text) {
        if(splitPane.getBottomComponent() != htmlView) {
            splitPane.setBottomComponent(htmlView);
        }
        if (text != null) {
            htmlPane.setContentType("text/html");
            htmlPane.setFont(HTML_DEFAULT_FONT);
            htmlPane.setText(text);
            htmlPane.setCaretPosition(0);
        } else {
            htmlPane.setText("");
        }
    }
    
    private void displayTable(HistogramTableModel htm) {
        topSplitPane.setRightComponent(null);
        htm.setFilter("");
        htm.setShowHotspotClasses(PrefManager.get().getShowHotspotClasses());
        
        TableSorter ts = new TableSorter(htm);
        histogramTable = new JTable(ts);
        ts.setTableHeader(histogramTable.getTableHeader());
        histogramTable.getColumnModel().getColumn(0).setPreferredWidth(700);
        tableView = new JScrollPane(histogramTable);
        
        JPanel histogramView = new JPanel(new BorderLayout());
        JPanel histoStatView = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        Font font = new Font("SansSerif", Font.PLAIN, 10);
        JLabel infoLabel = new JLabel(NumberFormat.getInstance().format(htm.getRowCount()) + " classes and base types");
        infoLabel.setFont(font);
        histoStatView.add(infoLabel);
        infoLabel = new JLabel(NumberFormat.getInstance().format(htm.getBytes()) + " bytes");
        infoLabel.setFont(font);
        histoStatView.add(infoLabel);
        infoLabel = new JLabel(NumberFormat.getInstance().format(htm.getInstances()) + " live objects");
        infoLabel.setFont(font);
        histoStatView.add(infoLabel);
        if(htm.isOOM()) {
            infoLabel = new JLabel("<html><b>OutOfMemory found!</b>");
            infoLabel.setFont(font);
            histoStatView.add(infoLabel);
        }
        if(htm.isIncomplete()) {
            infoLabel = new JLabel("<html><b>Class Histogram is incomplete! (broken logfile?)</b>");
            infoLabel.setFont(font);
            histoStatView.add(infoLabel);
        }
        JPanel filterPanel = new JPanel(new FlowLayout());
        infoLabel = new JLabel("Filter-Expression");
        infoLabel.setFont(font);
        filterPanel.add(infoLabel);
        
        filter = new JTextField(30);
        filter.setFont(font);
        filter.addCaretListener(new FilterListener(htm));
        filterPanel.add(infoLabel);
        filterPanel.add(filter);
        checkCase = new JCheckBox();
        checkCase.addChangeListener(new CheckCaseListener(htm));
        infoLabel = new JLabel("Ignore Case");
        infoLabel.setFont(font);
        filterPanel.add(infoLabel);
        filterPanel.add(checkCase);
        histoStatView.add(filterPanel);
        histogramView.add(histoStatView, BorderLayout.SOUTH);
        histogramView.add(tableView, BorderLayout.CENTER);
        
        histogramView.setPreferredSize(splitPane.getBottomComponent().getSize());
        
        splitPane.setBottomComponent(histogramView);
    }
    
    private class FilterListener implements CaretListener {
        HistogramTableModel htm;
        String currentText = "";
        FilterListener(HistogramTableModel htm) {
            this.htm = htm;
        }
        
        public void caretUpdate(CaretEvent event) {
            if(!filter.getText().equals(currentText)) {
                htm.setFilter(filter.getText());
                histogramTable.revalidate();
            }
        }
    }
    
    private class CheckCaseListener implements ChangeListener {
        HistogramTableModel htm;
        
        CheckCaseListener(HistogramTableModel htm) {
            this.htm = htm;
        }
        
        public void stateChanged(ChangeEvent e) {
            htm.setIgnoreCase(checkCase.isSelected());
            histogramTable.revalidate();
        }
    }
    
    private void addThreadDumps(DefaultMutableTreeNode top, InputStream dumpFileStream) {
        DumpParser dp = null;
        try {
            String fileName = top.getUserObject().toString();
            Map dumpMap = new HashMap();
            dumpStore.addFileToDumpFiles(fileName, dumpMap);
            dp = DumpParserFactory.get().getDumpParserForVersion("1.4", dumpFileStream, dumpMap);
            while(dp.hasMoreDumps()) {
                top.add(dp.parseNext());
            }
        } finally {
            if(dp != null) {
                try {
                    dp.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    /**
     * navigate to the currently selected dump in logfile
     */
    private void navigateToDumpInLogfile() {
        Object userObject = ((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent()).getUserObject();
        if(userObject instanceof ThreadInfo) {
            ThreadInfo ti = (ThreadInfo) userObject;
            int lineNumber = ti.threadName.indexOf("around") >= 0 ? 
                Integer.parseInt(ti.threadName.substring(ti.threadName.lastIndexOf(" line ")+6, 
                ti.threadName.lastIndexOf(" around "))) : 
                Integer.parseInt(ti.threadName.substring(ti.threadName.lastIndexOf(' ')+1));
            
            // find log file node.
            TreePath selPath = tree.getSelectionPath();
            while (selPath != null && !checkNameFromNode((DefaultMutableTreeNode) selPath.getLastPathComponent(), File.separator)) {

                selPath = selPath.getParentPath();
            }
            
            tree.setSelectionPath(selPath);
            tree.scrollPathToVisible(selPath);

            Enumeration childs = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).children();
            boolean found = false;
            DefaultMutableTreeNode logfileContent = null;
            while(!found && childs.hasMoreElements()) {
                logfileContent = (DefaultMutableTreeNode) childs.nextElement();
                found = logfileContent.getUserObject() instanceof LogFileContent;
            }
            
            if(found) {
                TreePath monitorPath = new TreePath(logfileContent.getPath());
                tree.setSelectionPath(monitorPath);
                tree.scrollPathToVisible(monitorPath);
                displayLogFileContent(logfileContent.getUserObject());
                jeditPane.setFirstLine(lineNumber-1);
            }
           
            System.out.println("lineNumber " + lineNumber);
        }
    }
    
    /**
     * navigate to monitor
     * @param monitorLink the monitor link to navigate to
     */
    private void navigateToMonitor(String monitorLink) {
        String monitor = monitorLink.substring(monitorLink.lastIndexOf('/')+1);
        
        // find monitor node for this thread info
        DefaultMutableTreeNode dumpNode = getDumpRootNode((DefaultMutableTreeNode) tree.getLastSelectedPathComponent());
        Enumeration childs = dumpNode.children();
        DefaultMutableTreeNode monitorNode = null;
        DefaultMutableTreeNode monitorWithoutLocksNode = null;
        while(childs.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) childs.nextElement();
            if(((Category) child.getUserObject()).getName().startsWith("Monitors (")) {
                monitorNode = child;
            } else if(((Category) child.getUserObject()).getName().startsWith("Monitors without")) {
                monitorWithoutLocksNode = child;
            }
        }
        
        // highlight chosen monitor
        JTree searchTree = ((Category) monitorNode.getUserObject()).getCatTree(this);
        TreePath searchPath = searchTree.getNextMatch(monitor,0,Position.Bias.Forward);
        if((searchPath == null) && (monitorWithoutLocksNode != null)) {
            searchTree = ((Category) monitorWithoutLocksNode.getUserObject()).getCatTree(this);
            searchPath = searchTree.getNextMatch(monitor,0,Position.Bias.Forward);
            monitorNode = monitorWithoutLocksNode;
        }
            
        if(searchPath != null) {
            TreePath monitorPath = new TreePath(monitorNode.getPath());
            tree.setSelectionPath(monitorPath);
            tree.scrollPathToVisible(monitorPath);
            
            displayCategory(monitorNode.getUserObject());
            
            TreePath threadInMonitor = searchPath.pathByAddingChild(((DefaultMutableTreeNode)searchPath.getLastPathComponent()).getLastChild());
            searchTree.setSelectionPath(threadInMonitor);
            searchTree.scrollPathToVisible(searchPath);
            searchTree.setSelectionPath(searchPath);
        }
    }
    
    /**
     * navigate to root node of currently active dump
     */
    private void navigateToDump() {
        TreePath currentPath = tree.getSelectionPath();
        tree.setSelectionPath(currentPath.getParentPath());
        tree.scrollPathToVisible(currentPath.getParentPath());
    }

    /**
     * navigate to child of currently selected node with the given prefix in name
     * @param startsWith node name prefix (e.g. "Threads waiting")
     */
    private void navigateToChild(String startsWith) {
        TreePath currentPath = tree.getSelectionPath();
        DefaultMutableTreeNode dumpNode = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
        Enumeration childs = dumpNode.children();
        
        TreePath searchPath = null;
        while((searchPath == null) && childs.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) childs.nextElement();
            String name = child.toString();
            if(name != null && name.startsWith(startsWith)) {
                searchPath = new TreePath(child.getPath());
            }
        }
        
        if(searchPath != null) {
            tree.makeVisible(searchPath);
            tree.setSelectionPath(searchPath);
            tree.scrollPathToVisible(searchPath);
        }
    }
    
    protected MainMenu getMainMenu() {
        return((MainMenu) frame.getJMenuBar());
    }
    
    public void createPopupMenu() {
        JMenuItem menuItem;
        
        //Create the popup menu.
        JPopupMenu popup = new JPopupMenu();
        
        /*menuItem = new JMenuItem("Fetch Thread Dump");
        menuItem.addActionListener(this);
        popup.add(menuItem);
        popup.addSeparator();*/
        
        menuItem = new JMenuItem("Diff Selection");
        menuItem.addActionListener(this);
        popup.add(menuItem);
        menuItem = new JMenuItem("Find long running threads...");
        menuItem.addActionListener(this);
        popup.add(menuItem);
        popup.addSeparator();
        menuItem = new JMenuItem("Parse loggc-logfile...");
        menuItem.addActionListener(this);
        popup.add(menuItem);
        menuItem = new JMenuItem("Close logfile...");
        menuItem.addActionListener(this);
        popup.add(menuItem);
        popup.addSeparator();
        showDumpMenuItem = new JMenuItem("Show selected Dump in logfile");
        showDumpMenuItem.addActionListener(this);
        showDumpMenuItem.setEnabled(false);
        popup.add(showDumpMenuItem);
        
        //Add listener to the text area so the popup menu can come up.
        MouseListener popupListener = new PopupListener(popup);
        tree.addMouseListener(popupListener);
    }
    
    private PopupListener catPopupListener = null;
    
    /**
     * create a instance of this menu for a category
     */
    private PopupListener getCatPopupMenu() {
        if(catPopupListener == null) {
            JMenuItem menuItem;
            
            //Create the popup menu.
            JPopupMenu popup = new JPopupMenu();
            
            menuItem = new JMenuItem("Search...");
            menuItem.addActionListener(this);
            popup.add(menuItem);
            
            //Add listener to the text area so the popup menu can come up.
            catPopupListener = new PopupListener(popup);
        }
        
        return(catPopupListener);
    }
    
    class PopupListener extends MouseAdapter {
        JPopupMenu popup;
        
        PopupListener(JPopupMenu popupMenu) {
            popup = popupMenu;
        }
        
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }
        
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }
        
        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(),
                        e.getX(), e.getY());
                showDumpMenuItem.setEnabled(((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent()).
                        getUserObject() instanceof ThreadInfo);
            }
        }
    }
        
    /**
     * check menu events
     */
    public void actionPerformed(ActionEvent e) {
        JMenuItem source = (JMenuItem)(e.getSource());
        if(source.getText().substring(1).startsWith(":\\") || source.getText().startsWith("/") ) {
            dumpFile = source.getText();
            openFiles(new File[] {new File(dumpFile)}, true);
        } else if("Open...".equals(source.getText())) {
            chooseFile();
        } else if("Open JMX Connection...".equals(source.getText())) {
            openJMXConnection(false);
        } else if("Open loggc file...".equals(source.getText())) {
            openLoggcFile();
        } else if("Preferences".equals(source.getText())) {
            showPreferencesDialog();
        } else if("Filters...".equals(source.getText())) {
            showFilterDialog();
        } else if("Exit TDA".equals(source.getText())) {
            saveState();
            frame.dispose();
        } else if("Overview".equals(source.getText())) {
            showHelpOverview();
        } else if("Release Notes".equals(source.getText())) {
            showReleaseNotes();
        } else if("License".equals(source.getText())) {
            showLicense();
        } else if("Forum".equals(source.getText())) {
            try {
                Browser.open("https://tda.dev.java.net/servlets/ForumMessageList?forumID=1967");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this.getRootPane(),
                        "Error opening TDA Online Forum\nPlease open https://tda.dev.java.net/servlets/ForumMessageList?forumID=1967 in your browser!",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else if("About TDA".equals(source.getText())) {
            showInfo();
        } else if("Search...".equals(source.getText())) {
            showSearchDialog();
        } else if("Apply Filter...".equals(source.getText())) {
            showApplyFilterDialog();
        } else if("Parse loggc-logfile...".equals(source.getText())) {
            parseLoggcLogfile();
        } else if("Find long running threads...".equals(source.getText())) {
            findLongRunningThreads();
        } else if("Close logfile...".equals(source.getText())) {
            closeCurrentDump();
        } else if("Close all...".equals(source.getText())) {
            closeAllDumps();
        } else if("Diff Selection".equals(source.getText())) {
            TreePath[] paths = tree.getSelectionPaths();
            if(paths.length < 2) {
                JOptionPane.showMessageDialog(this.getRootPane(),
                        "You must select at least two dumps for getting a diff!\n",
                        "Error", JOptionPane.ERROR_MESSAGE);
                
            } else {
                DefaultMutableTreeNode mergeRoot = fetchTop(tree.getSelectionPath());
                Map dumpMap = dumpStore.getFromDumpFiles(mergeRoot.getUserObject().toString());
                DumpParserFactory.get().getCurrentDumpParser().mergeDumps(mergeRoot,
                        dumpMap, paths, paths.length, null);
                createTree();
                this.getRootPane().revalidate();
            }
        } else if("Show selected Dump in logfile".equals(source.getText())) {
            navigateToDumpInLogfile();
        }
    }
    
    private void showInfo() {
        JOptionPane.showMessageDialog(this.getRootPane(),
                "<html><body>" +
                "<p>Java Version: " + System.getProperty("java.version") + "</p><br>" +
                "<p>Icons used are based on Benno System Icons by Benno Meyer.</p><br>" +
                "<p>Contains Classes from JEdit for logfile display.</p><br>" +
                "<p>TDA is free software; you can redistribute it and/or modify<br>" +
                "it under the terms of the Lesser GNU General Public License as published by<br>" +
                "the Free Software Foundation; either version 2.1 of the License, or<br>" +
                "(at your option) any later version.</p><br>" +
                "TDA is distributed in the hope that it will be useful,<br>" +
                "but WITHOUT ANY WARRANTY; without even the implied warranty of<br>" +
                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the<br>" +
                "Lesser GNU General Public License for more details.<p><br>" +
                "You should have received a copy of the Lesser GNU General Public License<br>" +
                "along with TDA; if not, write to the Free Software<br>" +
                "Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA<p>",
                "About TDA", JOptionPane.INFORMATION_MESSAGE, TDA.createImageIcon("tda-logo.jpg"));
        
    }
    
    /**
     * set the ui font for all tda stuff (needs to be done for create of objects)
     * @param f the font to user
     */
    private void setUIFont(javax.swing.plaf.FontUIResource f){
        //
        // sets the default font for all Swing components.
        // ex.
        //  setUIFont (new javax.swing.plaf.FontUIResource("Serif",Font.ITALIC,12));
        //
        java.util.Enumeration keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource)
                UIManager.put(key, f);
        }
    }
    
    private void showHelpOverview() {
        HelpOverviewDialog tutDialog = new HelpOverviewDialog(frame, "Overview", "doc/tutorial.html");
        tutDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        
        //Display the window.
        tutDialog.pack();
        tutDialog.setLocationRelativeTo(frame);
        tutDialog.setVisible(true);
        
    }
    
    private void showReleaseNotes() {
        HelpOverviewDialog tutDialog = new HelpOverviewDialog(frame, "Release Notes", "doc/README");
        tutDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        
        //Display the window.
        tutDialog.pack();
        tutDialog.setLocationRelativeTo(frame);
        tutDialog.setVisible(true);
        
    }
    
    private void showLicense() {
        HelpOverviewDialog tutDialog = new HelpOverviewDialog(frame, "License Information", "doc/COPYING");
        tutDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        
        //Display the window.
        tutDialog.pack();
        tutDialog.setLocationRelativeTo(frame);
        tutDialog.setVisible(true);
        
    }
    
    private void showPreferencesDialog() {
        //Create and set up the window.
        if(prefsDialog == null) {
            prefsDialog = new PreferencesDialog(frame);
            prefsDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        }
        
        frame.setEnabled(false);
        //Display the window.
        prefsDialog.reset();
        prefsDialog.pack();
        prefsDialog.setLocationRelativeTo(frame);
        prefsDialog.setVisible(true);
    }
    
    private void showFilterDialog() {
        
        //Create and set up the window.
        if(filterDialog == null) {
            filterDialog = new FilterDialog(frame);
            filterDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        }
        
        frame.setEnabled(false);
        //Display the window.
        filterDialog.reset();
        filterDialog.pack();
        filterDialog.setLocationRelativeTo(frame);
        filterDialog.setVisible(true);
    }
    
    /**
     * flag indicates if next file to open will be the first file (so fresh open)
     * or if a add has to be performed.
     */
    private boolean firstFile = true;
    
    /**
     * choose a log file.
     * @param addFile check if a log file should be added or if tree should be cleared.
     */
    private void chooseFile() {
        if(firstFile && (PrefManager.get().getPreferredSizeFileChooser().height > 0)) {
            fc.setPreferredSize(PrefManager.get().getPreferredSizeFileChooser());
        }
        int returnVal = fc.showOpenDialog(this.getRootPane());
        fc.setPreferredSize(fc.getSize());
        PrefManager.get().setPreferredSizeFileChooser(fc.getSize());
        
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            openFiles(files, false);
        }
    }
    
    /**
     * open the provided files. If isRecent is set to true, passed files
     * are not added to the recent file list.
     * @param files the files array to open
     * @param isRecent true, if passed files are from recent file list.
     */
    private void openFiles(File[] files, boolean isRecent) {
        for (int i = 0; i < files.length; i++) {
            dumpFile = files[i].getAbsolutePath();
            if(dumpFile != null) {
                if(!firstFile) {
                    // root nodes are moved down.
                    setRootNodeLevel(1);
                    
                    // do direct add without re-init.
                    addDumpFile();
                } else {
                    initDumpDisplay();
                    if(isFileOpen()) {
                       firstFile = false;
                    }
                }
            }
            
            if(!isRecent) {
                PrefManager.get().addToRecentFiles(files[i].getAbsolutePath());
            }
        }

        this.getRootPane().revalidate();
        displayContent(null);
    }
    
    /**
     * Returns an ImageIcon, or null if the path was invalid.
     */
    public static ImageIcon createImageIcon(String path) {
        java.net.URL imgURL = TDA.class.getResource("icons/" + path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }
    
    /**
     * search for dump root node of for given node
     * @param node starting to search for
     * @return root node returns null, if no root was found.
     */
    private DefaultMutableTreeNode getDumpRootNode(DefaultMutableTreeNode node) {
        // search for starting node
        while(node != null && !checkNameFromNode(node, "Full Thread Dump")) {
            node = (DefaultMutableTreeNode) node.getParent();
        }
        
        return(node);
    }
        
    /**
     * load a loggc log file based on the current selected thread dump
     */
    private void parseLoggcLogfile() {
        DefaultMutableTreeNode node = getDumpRootNode((DefaultMutableTreeNode) tree.getLastSelectedPathComponent());
        
        // get pos of this node in the thread dump hierarchy.
        int pos = node.getParent().getIndex(node);
        
        DumpParserFactory.get().getCurrentDumpParser().setDumpHistogramCounter(pos);
        openLoggcFile();
    }
    
    /**
     * close the currently selected dump.
     */
    private void closeCurrentDump() {
        TreePath selPath = tree.getSelectionPath();
        
        while(selPath != null && !checkNameFromNode((DefaultMutableTreeNode) selPath.getLastPathComponent(), File.separator)) {
            
            selPath = selPath.getParentPath();
        }
        
        Object[] options = { "Close File", "Cancel close" };
        
        String fileName = ((DefaultMutableTreeNode) selPath.getLastPathComponent()).getUserObject().toString();
        fileName = fileName.substring(fileName.indexOf('/'));
        
        int selectValue = JOptionPane.showOptionDialog(null, "<html><body>Are you sure, you want to close the currently selected dump file<br><b>" + fileName +
                "</b></body></html>", "Confirm closing...",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        
        // if first option "close file" is selected.
        if(selectValue == 0) {
            // remove stuff from the top nodes
            topNodes.remove(selPath.getLastPathComponent());
            
            if(topNodes.size() == 0) {
                // simply do a reinit, as there is anything to display
                removeAll();
                revalidate();
                
                init();
                getMainMenu().getLongMenuItem().setEnabled(false);
                getMainMenu().getCloseMenuItem().setEnabled(false);
                getMainMenu().getCloseAllMenuItem().setEnabled(false);
            } else {
                // rebuild jtree
                createTree();
            }
            revalidate();
        }
        
    }
    
    /**
     * close all open dumps
     */
    private void closeAllDumps() {        
        Object[] options = { "Close all", "Cancel close" };
        
        int selectValue = JOptionPane.showOptionDialog(null, "<html><body>Are you sure, you want to close all open dump files", "Confirm closing...",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        
        // if first option "close file" is selected.
        if(selectValue == 0) {
            // remove stuff from the top nodes
            topNodes = new Vector();
            
            // simply do a reinit, as there is anything to display
            resetMainPanel();
        }        
    }
    
    /**
     * reset the main panel to start up
     */
    private void resetMainPanel() {
        removeAll();
        revalidate();
        
        init();
        revalidate();
        
        getMainMenu().getLongMenuItem().setEnabled(false);
        getMainMenu().getCloseMenuItem().setEnabled(false);
        getMainMenu().getCloseAllMenuItem().setEnabled(false);
    }
    
    /**
     * check if name of node starts with passed string
     */
    private boolean checkNameFromNode(DefaultMutableTreeNode node, String startsWith) {
        Object info = node.getUserObject();
        String result = null;
        if(info instanceof ThreadInfo) {
            result = ((ThreadInfo) info).threadName;
        } else if (info instanceof DumpsBaseNode) {
            result = (String) ((DumpsBaseNode) info).getContent();
        }
        
        return(result != null && result.startsWith(startsWith));
    }
    
    /**
     * open and parse loggc file
     */
    private void openLoggcFile() {
        int returnVal = fc.showOpenDialog(this.getRootPane());
        
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            loggcFile = file.getAbsolutePath();
            if(loggcFile != null) {
                try {
                    final InputStream loggcFileStream = new ProgressMonitorInputStream(
                            this,
                            "Parsing " + loggcFile,
                            new FileInputStream(loggcFile));
                    
                    final SwingWorker worker = new SwingWorker() {
                        public Object construct() {
                            try {
                                DefaultMutableTreeNode top = fetchTop(tree.getSelectionPath());
                                
                                DumpParserFactory.get().getCurrentDumpParser().parseLoggcFile(loggcFileStream, top);
                                
                                addThreadDumps(top, dumpFileStream);
                                createTree();
                                getRootPane().revalidate();
                                displayContent(null);
                            } finally {
                                if(loggcFileStream != null) {
                                    try {
                                        loggcFileStream.close();
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    }
                                }
                            }
                            return null;
                        }
                    };
                    worker.start();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    /**
     * find long running threads either in all parsed thread dumps or in marked thread
     * dump range.
     */
    private void findLongRunningThreads() {
        TreePath[] paths = tree.getSelectionPaths();
        if((paths == null) || (paths.length < 2)) {
            JOptionPane.showMessageDialog(this.getRootPane(),
                    "You must select at least two dumps for long thread run detection!\n",
                    "Error", JOptionPane.ERROR_MESSAGE);
            
        } else {
            if(longThreadDialog == null) {
                DefaultMutableTreeNode mergeRoot = fetchTop(tree.getSelectionPath());
                Map dumpMap = dumpStore.getFromDumpFiles(mergeRoot.getUserObject().toString());
                
                longThreadDialog = new LongThreadDialog(this, paths, mergeRoot, dumpMap);
                longThreadDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            }
            
            frame.setEnabled(false);
            //Display the window.
            longThreadDialog.reset();
            longThreadDialog.pack();
            longThreadDialog.setLocationRelativeTo(frame);
            longThreadDialog.setVisible(true);
            
        }
    }
    
    private void openJMXConnection(boolean resetNodes) {
        if(jmxConnectionDialog == null) {
            jmxConnectionDialog = new JMXConnectDialog(this, frame, new DefaultMutableTreeNode());
            jmxConnectionDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        }
        
        frame.setEnabled(false);
        jmxConnectionDialog.reset();
        jmxConnectionDialog.pack();
        jmxConnectionDialog.setLocationRelativeTo(frame);
        jmxConnectionDialog.setVisible(true);
        
        if(resetNodes){
            topNodes = new Vector();
        }
    }
    
    public void addJMXConnection(RemoteConnection jmxConnection) {
        final DefaultMutableTreeNode top = new DefaultMutableTreeNode(jmxConnection);
        topNodes.add(top);
        // root nodes are moved down.
        setRootNodeLevel(1);
        
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                createTree();
                
                return null;
            }
        };
        worker.start();
    }
    
    private int rootNodeLevel = 0;
    
    private int getRootNodeLevel() {
        return(rootNodeLevel);
    }
    
    private void setRootNodeLevel(int value) {
        rootNodeLevel = value;
    }
    
    private DefaultMutableTreeNode fetchTop(TreePath pathToRoot) {
        return((DefaultMutableTreeNode) pathToRoot.getPathComponent(getRootNodeLevel()));
    }
    
    /**
     * save the application state to preferences.
     */
    private void saveState() {
        PrefManager.get().setWindowState(frame.getExtendedState());
        PrefManager.get().setSelectedPath(fc.getCurrentDirectory());
        PrefManager.get().setPreferredSize(frame.getRootPane().getSize());
        PrefManager.get().setWindowPos(frame.getX(), frame.getY());
        if(isThreadDisplay()) {
            PrefManager.get().setTopDividerPos(topSplitPane.getDividerLocation());
            PrefManager.get().setDividerPos(splitPane.getDividerLocation());
        }
        PrefManager.get().flush();
    }
    
    /**
     * trigger, if a file is opened
     */
    private boolean fileOpen = false;

    private boolean isFileOpen() {
        return fileOpen;
    }
    
    private void setFileOpen(boolean value) {
        fileOpen = value;
    }
    
    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        frame = new JFrame("TDA - Thread Dump Analyzer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        //Image image = Toolkit.getDefaultToolkit().getImage( "TDA.gif" );
        Image image = TDA.createImageIcon("TDA.gif").getImage();
        frame.setIconImage( image );
        
        
        frame.getRootPane().setPreferredSize(PrefManager.get().getPreferredSize());
        
        //Create and set up the content pane.
        if(dumpFile != null) {
            TDA.get().initDumpDisplay();
        }
        
        TDA.get().setOpaque(true); //content panes must be opaque
        frame.setContentPane(TDA.get());
        
        frame.setJMenuBar(new MainMenu(TDA.get()));
        
        // init filechooser
        fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setCurrentDirectory(PrefManager.get().getSelectedPath());
        
        /**
         * add window listener for persisting state of main frame
         */
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                TDA.get().saveState();
            }
            
            public void windowClosed(WindowEvent e) {
                System.exit(0);
            }
        });
        
        frame.setLocation(PrefManager.get().getWindowPos());
        
        //Display the window.
        frame.pack();
        
        // restore old window settings.
        frame.setExtendedState(PrefManager.get().getWindowState());
        
        frame.setVisible(true);
    }
    
    private void showApplyFilterDialog() {
        TreePath firstSelected = tree.getSelectionPath();
        Category cat = (Category) ((DefaultMutableTreeNode) firstSelected.getLastPathComponent()).getUserObject();
        ApplyFilterDialog applyFilterDialog = new ApplyFilterDialog(frame, cat);
        
        frame.setEnabled(false);
        
        //Display the window.
        applyFilterDialog.reset();
        applyFilterDialog.pack();
        applyFilterDialog.setLocationRelativeTo(frame);
        applyFilterDialog.setVisible(true);
        
        applyFilterDialog.addWindowListener(new WindowAdapter() {
                public void windowClosed(WindowEvent e) {
                    frame.setEnabled(true);
                }
            });
    }

    /**
     * display search dialog for current category
     */
    private void showSearchDialog() {
        // get the currently select category tree
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        JTree catTree = ((Category) node.getUserObject()).getCatTree(this);
        
        //Create and set up the window.
        searchDialog = new SearchDialog(frame, catTree);
        
        frame.setEnabled(false);
        //Display the window.
        searchDialog.reset();
        searchDialog.pack();
        searchDialog.setLocationRelativeTo(frame);
        searchDialog.setVisible(true);
        
        searchDialog.addWindowListener(new WindowAdapter() {
                public void windowClosed(WindowEvent e) {
                    frame.setEnabled(true);
                }
            });
    }
    
    /**
     * main startup method for TDA
     */
    public static void main(String[] args) {
        if(args.length > 0) {
            dumpFile = args[0];
        }
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }

    /**
     * check file menu
     */
    public void menuSelected(MenuEvent e) {
        JMenu source = (JMenu) e.getSource();
        if((source != null) && "File".equals(source.getText())) {
            // close menu item only active, if something is selected.
            getMainMenu().getCloseMenuItem().setEnabled(tree.getSelectionPath() != null);
        }
    }

    public void menuDeselected(MenuEvent e) {
        // nothing to do
    }

    public void menuCanceled(MenuEvent e) {
        // nothing to do
    }

    public static String getFontSizeModifier(int add) {
        String result = String.valueOf(fontSizeModifier + add);
        if((fontSizeModifier + add)> 0) {
            result = "+" + (fontSizeModifier + add);
        } 
        return(result);
    }
    
    public static void setFontSizeModifier(int value) {
        fontSizeModifier = value;
    }

    
}
