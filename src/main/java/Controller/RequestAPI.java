package Controller;

import Model.*;
import Viewer.GUI;
import org.apache.commons.io.IOUtils;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;

import org.json.*;



public class RequestAPI {
    private static RequestAPI requestInstance;

    private RequestAPI() {
        classRoomList = new ArrayList<>();
        classIdList = new ArrayList<>();
        TAList = new ArrayList<>();
        studentList = new ArrayList<>();
    }

    public static RequestAPI getInstance() {
        if (requestInstance == null) {
            requestInstance = new RequestAPI();
            client = HttpClients.createDefault();

        }
        return requestInstance;
    }

    public String getFilesPath() {
        return filesPath;
    }

    private final String filesPath = "src/Files/";

    private List<ClassRoom> classRoomList;

    public List<ClassRoom> getClassRoomList() {
        try {
            String filePath = RequestAPI.getInstance().getFilesPath() + "classRoomList.dat";
            classRoomList = IOSystem.getInstance().read(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(GUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return classRoomList;
    }
    private List<String> classIdList;
    private List<TA> TAList;
    private List<Student> studentList;
    private HttpGet get;
    private HttpPost post;
    private static HttpClient client;
    private URIBuilder builder;
    private CloseableHttpResponse response;
    private String token;

    public List<TA> getTAList() {
        return TAList;
    }
    public List<String> getClassIdList() {
        return classIdList;
    }
    public void setClassIdList(List<String> classIdList) {
        this.classIdList = classIdList;
    }
    public void setTAList(List<TA> TAList) {
        this.TAList = TAList;
    }
    public void login(String username, String password, boolean remember) throws IOException, URISyntaxException {
        // Init
        client = HttpClients.createDefault();
        // First Get request to get token
        String loginUrl = "https://crm.llv.edu.vn/index.php?module=Users&action=Login&mode=login";

        // Get request response (HTML)
        String content = getRequestContent(loginUrl, null, "GET");
//
        // Print request response
        System.out.println("Protocol: " + response.getProtocolVersion());
        System.out.println("Status:" + response.getStatusLine().toString());
        System.out.println("Content type:" + response.getEntity().getContentType());
        System.out.println("Locale:" + response.getLocale());
        System.out.println("Headers:");

        // Read response headers
        for(Header header : response.getAllHeaders()) {
            System.out.println("          " + header.getName()+": " + header.getValue());
        }

        // Get token in returned HTML
        Document doc = Jsoup.parse(content);
        Element element = doc.select("input").get(3);
        token = element.attr("value");
        System.out.println("Token: " + token);

        // Add form data to payload
        List<NameValuePair> payload = new ArrayList<>();
        payload.add(new BasicNameValuePair("__vtrftk", token));
        payload.add(new BasicNameValuePair("username", username));
        payload.add(new BasicNameValuePair("password", password));
        payload.add(new BasicNameValuePair("remember", remember ? "true" : "false"));

        content = getRequestContent(loginUrl, payload, "POST");
        System.out.println(content);

    }
    public void updateClassIdList() throws IOException, URISyntaxException {
        classIdList.clear();
        // Loop through all pages
        int page = 1;
        int totalPage = 0;

        String content = null;
        String on_goingClassListUrl = "https://crm.llv.edu.vn/index.php?module=Classes&parent=&page=1&view=List&viewname=493&orderby=schools&sortorder=ASC&search_params=%5B%5B%5B%22class_status%22%2C%22e%22%2C%22On-Going%22%5D%2C%5B%22schools%22%2C%22c%22%2C%22MD%22%5D%5D%5D";
        String totalPageJsonUrl = "https://crm.llv.edu.vn/index.php?__vtrftk=sid:0e4015d1f33aee007767349d620db9e2e515740b,1679154595&module=Classes&parent=&page=1&view=ListAjax&viewname=493&orderby=schools&sortorder=ASC&search_params=%5B%5B%5B%22class_status%22%2C%22e%22%2C%22On-Going%22%5D%2C%5B%22schools%22%2C%22c%22%2C%22MD%22%5D%5D%5D&mode=getPageCount";

        // Request
        content = getRequestContent(totalPageJsonUrl,Arrays.asList(new BasicNameValuePair("__vtrftk", token)) , "GET");

        // parsing json content
        JSONObject jo = new JSONObject(content);
        jo = jo.getJSONObject("result");
        totalPage = jo.getInt("page");

        // Get class id from each page
        while (page <= totalPage){
            content = getRequestContent(on_goingClassListUrl,Arrays.asList(new BasicNameValuePair("page", Integer.toString(page))) , "GET");
            Document doc = Jsoup.parse(content);

            // Get classId from response
            Elements elements = doc.select("tr");
            for (Element e : elements) {
                if (e.hasClass("listViewEntries")){
                    classIdList.add(e.attr("data-id"));
//                    System.out.println(e.attr("data-id"));
                }
            }
            page++;
        }
        classIdList = new ArrayList<>(new HashSet<>(classIdList));
        System.out.println("Total class updated: " + classIdList.size());

        // Write list to file
        IOSystem.getInstance().write( classIdList,filesPath + "classIdList.dat");
    }
    public void updateClassList() throws URISyntaxException, IOException, ClassNotFoundException {
        // Get classIdList from file
        classIdList = IOSystem.getInstance().read(filesPath + "classIdList.dat");
        TAList = IOSystem.getInstance().read(filesPath + "TAList.dat");
        studentList = IOSystem.getInstance().read(filesPath + "studentList.dat");

        int count = 0;

        // Loop through all classId
        for (String classId : classIdList){
            count++;
            
            ClassRoom classRoom = getClassRoomInformation(classId);
//            classRoom.display();
            classRoomList.add(classRoom);

            System.out.println("Added " + classRoom.getClassName() + " " + count + "/" + classIdList.size());
            System.out.println("----------------------------------------------------");
//            if (count==5) break;
        }
        // Write to file
        IOSystem.getInstance().write(classRoomList, filesPath + "classRoomList.dat");
    }
    public void updateTAList() throws URISyntaxException, IOException {
        // Clear TAList
        TAList.clear();

        // Loop through all pages
        int page = 1;
        int totalPage = 0;

        String content = null;
        String TAListUrl = "https://crm.llv.edu.vn/index.php?module=TeacherTA&parent=&page=1&view=List&viewname=648&orderby=schools&sortorder=ASC&search_params=%5B%5B%5B%22schools%22%2C%22c%22%2C%22MD%22%5D%2C%5B%22cf_1252%22%2C%22e%22%2C%22TA%22%5D%5D%5D";
        String totalPageJsonUrl = "https://crm.llv.edu.vn/index.php?__vtrftk=sid:6eb8d4459b055ecf6ca085e3895468c2a865dd75,1679155849&module=TeacherTA&parent=&page=1&view=ListAjax&viewname=648&orderby=schools&sortorder=ASC&search_params=%5B%5B%5B%22schools%22%2C%22c%22%2C%22MD%22%5D%2C%5B%22cf_1252%22%2C%22e%22%2C%22TA%22%5D%5D%5D&mode=getPageCount";

        // Request
        content = getRequestContent(totalPageJsonUrl,Arrays.asList(new BasicNameValuePair("__vtrftk", token)) , "GET");

        // parsing json content
        JSONObject jo = new JSONObject(content);
        jo = jo.getJSONObject("result");
        totalPage = jo.getInt("page");

        Set<TA> set = new HashSet<>();
        while (page <= totalPage){
            content = getRequestContent(TAListUrl,Arrays.asList(new BasicNameValuePair("page", Integer.toString(page))) , "GET");
            Document doc = Jsoup.parse(content);

            // Get TA from response
            Elements elements = doc.select("tr");
            for (Element e : elements) {
                if (e.hasClass("listViewEntries")){
                    String name = e.select(".listViewEntryValue").get(0).text() + " " + e.select(".listViewEntryValue").get(1).text();
                    String phone = e.select(".listViewEntryValue").get(2).text();
                    String email = e.select(".listViewEntryValue").get(3).text();

                    TA ta = new TA(name, phone, email);
                    set.add(ta);
//                    System.out.println(ta.getName());
                }
            }
            page++;
        }
        TAList.addAll(set);

        // Write to file
        IOSystem.getInstance().write(TAList, filesPath+ "TAList.dat");
    }
    public void updateStudentList() throws URISyntaxException, IOException {
        // Clear studentList
        studentList.clear();

        // Loop through all pages
        int page = 1;
        int totalPage = 0;

        String content = null;
        String studentListUrl = "https://crm.llv.edu.vn/index.php?module=Contacts&parent=&page=1&view=List&viewname=470&orderby=&sortorder=&search_params=%5B%5B%5D%5D";
        String totalPageJsonUrl = "https://crm.llv.edu.vn/index.php?__vtrftk=sid:797b1b65f176b662e920f223a74320f600987536,1679156710&module=Contacts&parent=&page=1&view=ListAjax&viewname=470&orderby=&sortorder=&search_params=%5B%5B%5D%5D&mode=getPageCount";

        // Request
        content = getRequestContent(totalPageJsonUrl,Arrays.asList(new BasicNameValuePair("__vtrftk", token)) , "GET");

        // parsing json content
        JSONObject jo = new JSONObject(content);
        jo = jo.getJSONObject("result");
        totalPage = jo.getInt("page");


        while (page <= totalPage){
            System.out.println("Page " + page + "/" + totalPage);
            content = getRequestContent(studentListUrl, Arrays.asList(new BasicNameValuePair("page", Integer.toString(page))), "GET");
            Document doc = Jsoup.parse(content);

            // Get TA from response
            Elements elements = doc.select("tr");
            for (Element e : elements) {
                if (e.hasClass("listViewEntries1")){
                    String studentId = e.attr("data-id");
                    Elements td = e.select("td");
                    String name = td.get(1).text() + " " + td.get(2).text();
                    studentList.add(new Student(name, studentId));
                }
            }
            page++;
        }

        // Write to file
        IOSystem.getInstance().write(studentList, filesPath+ "studentList.dat");
    }
    public void run() throws IOException, URISyntaxException, ClassNotFoundException  {
        // Login
        login("dangminh.TAMD", "LLVN123456", true);
//        updateTAList();
//        updateStudentList();

//        List<TA> list = IOSystem.getInstance().read(filesPath + "TAList.dat");
//        for (TA ta : list) {
//            System.out.println(ta.getName() + " - " + ta.getPhoneNumber() + " - " + ta.getEmail());
//        }
//
//        for (String s : classIdList) {
//            System.out.println(s);
//        }
//        updateClassIdList();
//        updateClassList();
//        List<ClassRoom> list = IOSystem.getInstance().read(filesPath + "classRoomList.dat");
//        for (ClassRoom classRoom : list) {
//            System.out.println(classRoom.getClassName());
//            System.out.println(classRoom.getLatestLesson());
//        }
//        System.out.println(list.size());
    }
    public String getRequestContent(String url, List<NameValuePair> payload, String method) throws URISyntaxException, IOException {
        // Build url
        builder = new URIBuilder(url);
        if (payload != null)builder.addParameters(payload);

        // Request
        get = new HttpGet(builder.build());
        post = new HttpPost(builder.build());

        if (method.equals("POST")) {
            response = (CloseableHttpResponse) client.execute(post);
        } else {
            response = (CloseableHttpResponse) client.execute(get);
        }

        // Get content from response
        String content = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
        return content;
    }
    public ClassRoom getClassRoomInformation(String classId){   
            // Get class information
            String classLessonContentUrl = "https://crm.llv.edu.vn/index.php?module=Classes&relatedModule=SJLessonContent&view=Detail&record=&mode=showRelatedList&tab_label=Lesson%20Content";
            String classAttendanceUrl = "https://crm.llv.edu.vn/index.php?module=Classes&relatedModule=AttendanceClass&view=Detail&record=456177&mode=showRelatedList&tab_label=Attendance%20Report";
            String content = null;
        try {
            content = getRequestContent(classLessonContentUrl, Arrays.asList(new BasicNameValuePair("record", classId)), "GET");
        } catch (URISyntaxException ex) {
            Logger.getLogger(RequestAPI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RequestAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
            Document doc = Jsoup.parse(content);

            // Get class from response
            Elements elements = doc.select("td");

            TimeOFWeek timeOFWeek = new TimeOFWeek();
            List <TimeOFWeek> listTimeOfWeek = new ArrayList<>();
            List<TA> listTA = new ArrayList<>();
            List<String> listTAName = new ArrayList<>();
            String classCode = "";
            LocalDate startDate = null;
            LocalDate endDate = null;

            boolean foundTA = false;
            int countWeekDay = 0;
            // Get class code, day and time
            for (Element e : elements){
                // Found class code
                if (e.text().equals("Class Code")){
                    classCode = e.nextElementSibling().text();
//                    System.out.println(classCode);
                }

                // Get start and end date
                if (e.text().equals("Start Date")){
                    String start = e.nextElementSibling().text();
                    startDate = LocalDate.parse(start, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
                if (e.text().equals("End Date")){
                    String end = e.nextElementSibling().text();
                    endDate = LocalDate.parse(end, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }

                // Found TA
                if (e.text().equals("TA") && !foundTA){
                    foundTA = true;
                    String TAName = e.nextElementSibling().text();
                    listTAName = Arrays.asList(TAName.split(", "));

                    // Get TA by name
                    for (String name : listTAName){
//                        System.out.println("\""+name+"\"");
                        for (TA ta : TAList){
                            if (ta.getName().equals(name)){
//                                System.out.println("Found "+ name + " in TAList");
                                listTA.add(ta);
                                break;
                            }
                        }
                    }
                }

                // Found day and time ** has json file
                if (e.hasClass("weekDay") && !e.text().equals("")){
                    countWeekDay++;
                    int dayOfWeek = Integer.parseInt(e.attr("value"));
                    if (dayOfWeek == 0) dayOfWeek = 7;

                    String startTime = e.text().split(" ")[0];
                    LocalTime time = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));

                    timeOFWeek.setDayOfWeek(DayOfWeek.of(dayOfWeek));
                    timeOFWeek.setTime(time);

//                    System.out.println(timeOFWeek.getDayOfWeek() + " - " + timeOFWeek.getTime());

                    listTimeOfWeek.add(timeOFWeek);
                }


                // Found all class information
                if (listTimeOfWeek.size() == 2 || countWeekDay == 7){
                    break;
                };
            }

            // Get lesson list information
            List<Lesson>  lessonList = new ArrayList<>();

            String lessonNumber = null;
            String lessonId = null;
            String lessonName = null;
            LocalDate lessonDate = null;
            LocalTime lessonTime = null;
            String emailStatus = null;
            String lessonStatus = null;

//            // request with attendance page
//            elements = doc.select(".table-bordered");
//            System.out.println(elements.size());
//            elements = elements.get(2).select("tr");

            // request with lesson content page
            elements = doc.select(".item__lesson");

            for (Element e : elements){
//                System.out.println(e.text());
                Elements td = e.select("td");
                lessonNumber = td.get(0).text();
                lessonId = td.get(8).select(".btnSendEmail").attr("data-id");
                lessonName = td.get(1).text();
                String date = td.get(2).text();

                // Get lesson date
                if (date.equals("")){ // lesson not started
                    if (lessonNumber.equals("1") || lessonNumber.equals("21")){   // First lesson
                        lessonDate = startDate;
                    } else {
                        Lesson previousLesson = lessonList.get(lessonList.size()-1);
                        lessonDate = previousLesson.getDate().plusDays(7);
                    }
                } else {
                    lessonDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }

                emailStatus = td.get(7).text();
                lessonList.add(new Lesson(lessonNumber, lessonId, lessonName, lessonDate, null, emailStatus));
//                System.out.println(lessonNumber + " - " + lessonId + " - " + lessonName + " - " + lessonDate + " - " + emailStatus);
                }

            // *Get student list
            List<Student> listStudent = new ArrayList<>();

            // Get first lesson detail to extract student list
            String firstLessonDetailUrl = "https://crm.llv.edu.vn/index.php?module=AttendanceClass&action=AjaxListAtten&mode=listStudent&id=456177&lessonId=181942";

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("id", classId));
            params.add(new BasicNameValuePair("lessonId", lessonId));

        try {
            // Json file of student list
            content = getRequestContent(firstLessonDetailUrl, params, "GET");
        } catch (URISyntaxException ex) {
            Logger.getLogger(RequestAPI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RequestAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
//        if (content != null) return null;
            JSONObject jo = null;
        try{
            jo = new JSONObject(content);
        } catch (JSONException e) {
            System.out.println("@"+content + classId +"@");
        }
        JSONArray ja = jo.getJSONArray("result");

//            System.out.println(studentList.get(0).getName() + " - " + studentList.get(0).getId());

            // Loop through all student
            for (int i = 0; i < ja.length(); i++){
                JSONObject student = ja.getJSONObject(i);
//                System.out.println(student.toString());
                String studentId = student.getString("studentid");
//                System.out.println(studentId);

                // Get student by id
                for (Student s : studentList){
                    if (s.getId().equals(studentId)){
//                        System.out.println("Found "+ s.getName() + " in studentList");
                        listStudent.add(s);
                        break;
                    }
                }
            }

            ClassRoom classRoom = new ClassRoom(classId,classCode ,listTA, startDate, endDate, listTimeOfWeek, lessonList, listStudent);
        return classRoom;
    }
}
