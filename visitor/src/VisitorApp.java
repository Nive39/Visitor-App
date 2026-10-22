import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.imageio.ImageIO;

// QR
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.BarcodeFormat;

// PDF
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.Image;

// OpenCV
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

public class VisitorApp extends JFrame {

    JTextField nameField, phoneField, purposeField;
    JLabel qrLabel, photoLabel;

    JTable table;
    DefaultTableModel model;
    TableRowSorter<DefaultTableModel> sorter;

    BufferedImage currentImage;
    ArrayList<BufferedImage> visitorImages = new ArrayList<>();

    Map<String, Integer> scanCount = new HashMap<>();
    String lastQRData = "";

    boolean darkMode = false;

    public VisitorApp() {
        showLogin();
    }

    void showLogin() {

        JFrame login = new JFrame("Admin Login");
        login.setSize(350,250);
        login.setLayout(null);

        login.getContentPane().setBackground(new Color(200,220,240));

        JLabel userL = new JLabel("Username");
        userL.setBounds(40,60,100,25);

        JTextField user = new JTextField();
        user.setBounds(140,60,140,25);

        JLabel passL = new JLabel("Password");
        passL.setBounds(40,100,100,25);

        JPasswordField pass = new JPasswordField();
        pass.setBounds(140,100,140,25);

        JButton loginBtn = new JButton("Login");
        loginBtn.setBounds(110,150,120,35);

        loginBtn.addActionListener(e->{

            if(user.getText().equals("admin") &&
               new String(pass.getPassword()).equals("1234")){

                login.dispose();
                initUI();

            } else {

                JOptionPane.showMessageDialog(login,"Invalid Login");

            }

        });

        login.add(userL);
        login.add(user);
        login.add(passL);
        login.add(pass);
        login.add(loginBtn);

        login.setLocationRelativeTo(null);
        login.setVisible(true);
    }

    void initUI() {

        try{
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }catch(Exception e){
            JOptionPane.showMessageDialog(this,"OpenCV not loaded!");
        }

        setTitle("Visitor Management System");
        setSize(1150,680);
        setLayout(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        getContentPane().setBackground(new Color(200,220,240));

        Font labelFont = new Font("Segoe UI", Font.BOLD, 15);

        addLabel("Name",40,90,labelFont);
        addLabel("Phone",40,140,labelFont);
        addLabel("Purpose",40,190,labelFont);

        nameField = addField(150,90);
        phoneField = addField(150,140);
        purposeField = addField(150,190);

        addButton("Open Camera",40,250,e->captureFace());
        addButton("Generate QR",200,250,e->generateQR());
        addButton("Scan QR",40,300,e->simulateScan());
        addButton("Export PDF",200,300,e->exportPDF());
        addButton("Clear",120,350,e->clearFields());

        JButton themeBtn = new JButton("Toggle Dark Mode");
        themeBtn.setBounds(40,400,310,35);
        themeBtn.addActionListener(e->toggleTheme());
        add(themeBtn);

        qrLabel = createBox("QR Code");
        qrLabel.setBounds(40,450,150,150);
        add(qrLabel);

        photoLabel = createBox("Visitor Photo");
        photoLabel.setBounds(210,450,150,150);
        add(photoLabel);

        model = new DefaultTableModel();
        model.setColumnIdentifiers(new String[]{"Name","Phone","Purpose","Entry","Exit"});

        table = new JTable(model);
        table.setRowHeight(28);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(420,100,680,500);
        add(sp);

        addDateFilter();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    void addDateFilter(){

        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setBounds(420,60,50,25);
        add(filterLabel);

        String[] options = {"All","Today","This Week"};
        JComboBox<String> filterBox = new JComboBox<>(options);
        filterBox.setBounds(470,60,150,25);

        filterBox.addActionListener(e->{

            String selected = filterBox.getSelectedItem().toString();

            sorter.setRowFilter(new RowFilter<DefaultTableModel,Integer>(){

                public boolean include(Entry<? extends DefaultTableModel,? extends Integer> entry){

                    if(selected.equals("All"))
                        return true;

                    String entryTime = entry.getStringValue(3);
                    LocalDate date = LocalDate.parse(entryTime.substring(0,10));

                    if(selected.equals("Today"))
                        return date.equals(LocalDate.now());

                    if(selected.equals("This Week"))
                        return date.isAfter(LocalDate.now().minusDays(7));

                    return true;
                }
            });

        });

        add(filterBox);
    }

    void toggleTheme(){

        if(!darkMode){

            getContentPane().setBackground(new Color(40,40,40));
            table.setBackground(new Color(60,60,60));
            table.setForeground(Color.WHITE);

            darkMode = true;

        } else {

            getContentPane().setBackground(new Color(200,220,240));
            table.setBackground(Color.WHITE);
            table.setForeground(Color.BLACK);

            darkMode = false;
        }
    }

    JLabel createBox(String title){

        JLabel l = new JLabel("",SwingConstants.CENTER);

        l.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                title));

        l.setBackground(Color.WHITE);
        l.setOpaque(true);

        return l;
    }

    void addLabel(String t,int x,int y,Font f){

        JLabel l = new JLabel(t);
        l.setBounds(x,y,100,25);
        l.setFont(f);
        add(l);
    }

    JTextField addField(int x,int y){

        JTextField tf = new JTextField();
        tf.setBounds(x,y,180,30);
        add(tf);
        return tf;
    }

    void addButton(String text,int x,int y,ActionListener a){

        JButton b = new JButton(text);
        b.setBounds(x,y,150,35);
        b.addActionListener(a);
        add(b);
    }

    void captureFace(){

        new Thread(()->{

            VideoCapture cam = new VideoCapture(0);

            if(!cam.isOpened()){
                JOptionPane.showMessageDialog(this,"Camera not found");
                return;
            }

            Mat frame = new Mat();
            cam.read(frame);

            Mat resized = new Mat();
            Imgproc.resize(frame,resized,new Size(150,150));

            BufferedImage img=new BufferedImage(150,150,BufferedImage.TYPE_3BYTE_BGR);

            for(int y=0;y<150;y++){
                for(int x=0;x<150;x++){

                    double[] rgb=resized.get(y,x);
                    int pixel=((int)rgb[2]<<16)|((int)rgb[1]<<8)|(int)rgb[0];
                    img.setRGB(x,y,pixel);

                }
            }

            currentImage = img;
            photoLabel.setIcon(new ImageIcon(img));

            cam.release();

        }).start();
    }

    void generateQR(){

        try{

            String name=nameField.getText();
            String phone=phoneField.getText();

            lastQRData = name+"-"+phone;

            QRCodeWriter writer=new QRCodeWriter();

            BitMatrix matrix=writer.encode(lastQRData,
                    BarcodeFormat.QR_CODE,150,150);

            BufferedImage img=new BufferedImage(150,150,BufferedImage.TYPE_INT_RGB);

            for(int x=0;x<150;x++)
                for(int y=0;y<150;y++)
                    img.setRGB(x,y,matrix.get(x,y)?0:0xFFFFFF);

            qrLabel.setIcon(new ImageIcon(img));

            if(currentImage!=null)
                visitorImages.add(currentImage);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    void simulateScan(){

        if(lastQRData.isEmpty()) return;

        int count = scanCount.getOrDefault(lastQRData,0);
        String[] p = lastQRData.split("-");

        if(count==0){

            model.addRow(new Object[]{p[0],p[1],purposeField.getText(),currentTime(),""});
            scanCount.put(lastQRData,1);

        }

        else if(count==1){

            for(int i=0;i<model.getRowCount();i++){

                if(model.getValueAt(i,0).equals(p[0]) &&
                        model.getValueAt(i,4).toString().isEmpty()){

                    model.setValueAt(currentTime(),i,4);
                }
            }

            scanCount.put(lastQRData,2);
        }
    }

    String currentTime(){

        return LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    void exportPDF(){

        try{

            String path = System.getProperty("user.home")+"\\Desktop\\Visitor_Report.pdf";

            Document doc = new Document(com.itextpdf.text.PageSize.A4.rotate());

            PdfWriter.getInstance(doc,new FileOutputStream(path));

            doc.open();

            doc.add(new Paragraph("VISITOR REPORT\n\n"));

            PdfPTable t = new PdfPTable(6);

            t.addCell("Name");
            t.addCell("Phone");
            t.addCell("Purpose");
            t.addCell("Entry");
            t.addCell("Exit");
            t.addCell("Photo");

            for(int i=0;i<model.getRowCount();i++){

                t.addCell(model.getValueAt(i,0).toString());
                t.addCell(model.getValueAt(i,1).toString());
                t.addCell(model.getValueAt(i,2).toString());
                t.addCell(model.getValueAt(i,3).toString());
                t.addCell(model.getValueAt(i,4).toString());

                if(i < visitorImages.size() && visitorImages.get(i)!=null){

                    BufferedImage img = visitorImages.get(i);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img,"png",baos);

                    Image pdfImg = Image.getInstance(baos.toByteArray());
                    pdfImg.scaleToFit(60,60);

                    PdfPCell cell = new PdfPCell(pdfImg,true);
                    t.addCell(cell);

                }

                else{

                    t.addCell("No Photo");
                }
            }

            doc.add(t);
            doc.close();

            JOptionPane.showMessageDialog(this,"PDF Saved to Desktop!");

        }

        catch(Exception e){
            e.printStackTrace();
        }
    }

    void clearFields(){

        nameField.setText("");
        phoneField.setText("");
        purposeField.setText("");

        qrLabel.setIcon(null);
        photoLabel.setIcon(null);
    }

    public static void main(String[] args){

        new VisitorApp();
    }
}