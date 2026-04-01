package com.jobportal.resumeparser.parser;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ResumeParser {

    private static final Logger log = LoggerFactory.getLogger(ResumeParser.class);
    private static final Tika TIKA = new Tika();

    // ═══════════════════════════════════════
    // OpenNLP NameFinder (injected by Spring)
    // ═══════════════════════════════════════
    @Autowired
    private NameFinderME nameFinder;

    static {
        TIKA.setMaxStringLength(500 * 1024);
    }

    // ═══════════════════════════════════════
    // REGEX PATTERNS
    // ═══════════════════════════════════════
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+91[\\-\\s]?)?[6-9]\\d{9}(?!\\d)");

    private static final Pattern LINKEDIN_PATTERN =
            Pattern.compile("https?://(www\\.)?linkedin\\.com/in/[a-zA-Z0-9\\-_/]+");

    private static final Pattern GITHUB_PATTERN =
            Pattern.compile("https?://(www\\.)?github\\.com/[a-zA-Z0-9\\-_/]+");

    private static final Pattern PORTFOLIO_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9\\-]+\\.(vercel\\.app|netlify\\.app|github\\.io|herokuapp\\.com|web\\.app)[/\\w\\-]*");

    private static final Pattern EXPERIENCE_YEARS_PATTERN =
            Pattern.compile("(\\d{1,2})\\+?\\s*(?:years?|yrs?)(?:\\s*(?:and|&)\\s*(\\d{1,2})\\s*(?:months?|mos?))?",
                    Pattern.CASE_INSENSITIVE);

    // Section header patterns
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^\\s*(?:#+\\s*)?(?:" +
                    "objective|summary|profile|about\\s*me|career\\s*(?:objective|summary|profile)|professional\\s*summary|" +
                    "education|academic|qualification|educational\\s*(?:details|qualification|background)|" +
                    "experience|work\\s*(?:experience|history)|employment|professional\\s*experience|internship|" +
                    "skill|technical\\s*skill|key\\s*skill|core\\s*competenc|" +
                    "project|academic\\s*project|personal\\s*project|" +
                    "certif|achievement|award|honor|honour|" +
                    "language|hobbi|interest|extra\\s*curricular|" +
                    "contact|personal\\s*(?:info|detail)|declaration|reference" +
                    ")s?\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ═══════════════════════════════════════
    // NOISE WORDS — lines that should NEVER be a name
    // ═══════════════════════════════════════
    private static final Set<String> NOISE_WORDS = Set.of(
            "resume", "curriculum vitae", "curriculam vitae", "cv", "biodata",
            "objective", "summary", "profile", "about me", "career objective",
            "education", "academic", "qualification", "educational details",
            "experience", "work experience", "employment", "professional experience",
            "skill", "skills", "technical skills", "key skills", "core competencies",
            "project", "projects", "academic projects", "personal projects",
            "certification", "certifications", "achievement", "achievements",
            "language", "languages", "hobbies", "interests", "extra curricular",
            "contact", "personal info", "personal details", "declaration", "reference",
            "developer", "engineer", "analyst", "designer", "architect",
            "software developer", "software engineer", "web developer", "data analyst",
            "full stack", "frontend", "backend", "devops", "data science",
            "machine learning", "artificial intelligence", "enthusiast",
            "top skills", "strong professional", "professional career",
            "year of passing", "year of", "name of exam", "name of",
            "institute name", "educational qualification", "contribution",
            "master of computer applications", "bachelor of", "master of",
            "ms word", "student from", "data science ml", "data science ml enthusiast"
    );

    // ═══════════════════════════════════════
    // LOCATION SET — Indian cities/states
    // ═══════════════════════════════════════
    private static final Map<String, String> LOCATION_MAP = new LinkedHashMap<>();

    static {
        // Major metros
        LOCATION_MAP.put("chennai", "Chennai");
        LOCATION_MAP.put("bangalore", "Bangalore");
        LOCATION_MAP.put("bengaluru", "Bangalore");
        LOCATION_MAP.put("hyderabad", "Hyderabad");
        LOCATION_MAP.put("mumbai", "Mumbai");
        LOCATION_MAP.put("delhi", "Delhi");
        LOCATION_MAP.put("new delhi", "New Delhi");
        LOCATION_MAP.put("kolkata", "Kolkata");
        LOCATION_MAP.put("pune", "Pune");
        LOCATION_MAP.put("ahmedabad", "Ahmedabad");
        LOCATION_MAP.put("jaipur", "Jaipur");
        LOCATION_MAP.put("lucknow", "Lucknow");
        LOCATION_MAP.put("chandigarh", "Chandigarh");
        LOCATION_MAP.put("noida", "Noida");
        LOCATION_MAP.put("gurgaon", "Gurgaon");
        LOCATION_MAP.put("gurugram", "Gurugram");
        LOCATION_MAP.put("kochi", "Kochi");
        LOCATION_MAP.put("coimbatore", "Coimbatore");
        LOCATION_MAP.put("thiruvananthapuram", "Thiruvananthapuram");
        LOCATION_MAP.put("trivandrum", "Trivandrum");
        LOCATION_MAP.put("indore", "Indore");
        LOCATION_MAP.put("nagpur", "Nagpur");
        LOCATION_MAP.put("visakhapatnam", "Visakhapatnam");
        LOCATION_MAP.put("vizag", "Vizag");
        LOCATION_MAP.put("bhopal", "Bhopal");
        LOCATION_MAP.put("patna", "Patna");
        LOCATION_MAP.put("vadodara", "Vadodara");
        LOCATION_MAP.put("surat", "Surat");
        LOCATION_MAP.put("mysore", "Mysore");
        LOCATION_MAP.put("mysuru", "Mysuru");
        LOCATION_MAP.put("madurai", "Madurai");
        LOCATION_MAP.put("trichy", "Trichy");
        LOCATION_MAP.put("tiruchirappalli", "Tiruchirappalli");
        LOCATION_MAP.put("salem", "Salem");
        LOCATION_MAP.put("karur", "Karur");
        LOCATION_MAP.put("erode", "Erode");
        LOCATION_MAP.put("tirunelveli", "Tirunelveli");
        LOCATION_MAP.put("thanjavur", "Thanjavur");
        LOCATION_MAP.put("dindigul", "Dindigul");
        LOCATION_MAP.put("vellore", "Vellore");
        LOCATION_MAP.put("theni", "Theni");
        // TN states
        LOCATION_MAP.put("tamil nadu", "Tamil Nadu");
        LOCATION_MAP.put("tamilnadu", "Tamil Nadu");
        LOCATION_MAP.put("karnataka", "Karnataka");
        LOCATION_MAP.put("kerala", "Kerala");
        LOCATION_MAP.put("telangana", "Telangana");
        LOCATION_MAP.put("andhra pradesh", "Andhra Pradesh");
        LOCATION_MAP.put("maharashtra", "Maharashtra");
    }

    // ═══════════════════════════════════════
    // SKILLS SET — comprehensive
    // ═══════════════════════════════════════
    private static final Map<String, String> SKILLS_MAP = new LinkedHashMap<>();

    static {
        // Programming Languages
        SKILLS_MAP.put("\\bjava\\b", "Java");
        SKILLS_MAP.put("\\bpython\\b", "Python");
        SKILLS_MAP.put("\\bjavascript\\b", "JavaScript");
        SKILLS_MAP.put("\\btypescript\\b", "TypeScript");
        SKILLS_MAP.put("\\bc\\+\\+\\b", "C++");
        SKILLS_MAP.put("\\bc#\\b", "C#");
        SKILLS_MAP.put("\\b(?<!objective[- ])c\\b", "C");
        SKILLS_MAP.put("\\bruby\\b", "Ruby");
        SKILLS_MAP.put("\\bphp\\b", "PHP");
        SKILLS_MAP.put("\\bswift\\b", "Swift");
        SKILLS_MAP.put("\\bkotlin\\b", "Kotlin");
        SKILLS_MAP.put("\\bgo(?:lang)?\\b", "Go");
        SKILLS_MAP.put("\\brust\\b", "Rust");
        SKILLS_MAP.put("\\bscala\\b", "Scala");
        SKILLS_MAP.put("\\bperl\\b", "Perl");
        SKILLS_MAP.put("\\br\\b(?=\\s*(?:programming|language|studio))", "R");
        SKILLS_MAP.put("\\bmatlab\\b", "MATLAB");
        SKILLS_MAP.put("\\bdart\\b", "Dart");

        // Web Frontend
        SKILLS_MAP.put("\\breact(?:\\.?js)?\\b", "React");
        SKILLS_MAP.put("\\bangular(?:\\.?js)?\\b", "Angular");
        SKILLS_MAP.put("\\bvue(?:\\.?js)?\\b", "Vue.js");
        SKILLS_MAP.put("\\bnext\\.?js\\b", "Next.js");
        SKILLS_MAP.put("\\bnuxt\\.?js\\b", "Nuxt.js");
        SKILLS_MAP.put("\\bsvelte\\b", "Svelte");
        SKILLS_MAP.put("\\bhtml5?\\b", "HTML");
        SKILLS_MAP.put("\\bcss3?\\b", "CSS");
        SKILLS_MAP.put("\\bsass\\b", "SASS");
        SKILLS_MAP.put("\\bless\\b(?=\\s*(?:css|preprocessor))", "LESS");
        SKILLS_MAP.put("\\btailwind(?:\\s*css)?\\b", "Tailwind CSS");
        SKILLS_MAP.put("\\bbootstrap\\b", "Bootstrap");
        SKILLS_MAP.put("\\bjquery\\b", "jQuery");
        SKILLS_MAP.put("\\bredux\\b", "Redux");

        // Web Backend / Frameworks
        SKILLS_MAP.put("\\bspring\\s*boot\\b", "Spring Boot");
        SKILLS_MAP.put("\\bspring(?!\\s*boot)\\b", "Spring");
        SKILLS_MAP.put("\\bnode\\.?js\\b", "Node.js");
        SKILLS_MAP.put("\\bexpress\\.?js\\b", "Express.js");
        SKILLS_MAP.put("\\bdjango\\b", "Django");
        SKILLS_MAP.put("\\bflask\\b", "Flask");
        SKILLS_MAP.put("\\bfast\\s*api\\b", "FastAPI");
        SKILLS_MAP.put("\\bruby on rails\\b|\\brails\\b", "Ruby on Rails");
        SKILLS_MAP.put("\\basp\\.?net\\b", "ASP.NET");
        SKILLS_MAP.put("\\blaravel\\b", "Laravel");
        SKILLS_MAP.put("\\bhibernate\\b", "Hibernate");
        SKILLS_MAP.put("\\bmicroservices?\\b", "Microservices");
        SKILLS_MAP.put("\\brest(?:ful)?\\s*api\\b", "REST API");
        SKILLS_MAP.put("\\bgraphql\\b", "GraphQL");
        SKILLS_MAP.put("\\bgrpc\\b", "gRPC");
        SKILLS_MAP.put("\\bservlet\\b", "Servlet");
        SKILLS_MAP.put("\\bjsp\\b", "JSP");

        // Databases
        SKILLS_MAP.put("\\bmysql\\b", "MySQL");
        SKILLS_MAP.put("\\bpostgres(?:ql)?\\b", "PostgreSQL");
        SKILLS_MAP.put("\\bmongodb\\b", "MongoDB");
        SKILLS_MAP.put("\\boracle\\b(?=\\s*(?:db|database|sql|11g|12c|19c))?", "Oracle");
        SKILLS_MAP.put("\\bsql\\s*server\\b", "SQL Server");
        SKILLS_MAP.put("\\bredis\\b", "Redis");
        SKILLS_MAP.put("\\bcassandra\\b", "Cassandra");
        SKILLS_MAP.put("\\bfirebase\\b", "Firebase");
        SKILLS_MAP.put("\\bdynamodb\\b", "DynamoDB");
        SKILLS_MAP.put("\\belasticsearch\\b", "Elasticsearch");
        SKILLS_MAP.put("\\bsqlite\\b", "SQLite");
        SKILLS_MAP.put("\\bmariadb\\b", "MariaDB");
        SKILLS_MAP.put("\\bpl/?sql\\b", "PL/SQL");
        SKILLS_MAP.put("\\bsql\\b", "SQL");

        // Cloud / DevOps
        SKILLS_MAP.put("\\baws\\b", "AWS");
        SKILLS_MAP.put("\\bazure\\b", "Azure");
        SKILLS_MAP.put("\\bgcp\\b|\\bgoogle cloud\\b", "Google Cloud");
        SKILLS_MAP.put("\\bdocker\\b", "Docker");
        SKILLS_MAP.put("\\bkubernetes\\b|\\bk8s\\b", "Kubernetes");
        SKILLS_MAP.put("\\bjenkins\\b", "Jenkins");
        SKILLS_MAP.put("\\bterraform\\b", "Terraform");
        SKILLS_MAP.put("\\bansible\\b", "Ansible");
        SKILLS_MAP.put("\\bci/?cd\\b", "CI/CD");
        SKILLS_MAP.put("\\bnginx\\b", "Nginx");
        SKILLS_MAP.put("\\bapache\\b(?=\\s*(?:server|http|tomcat|web))?", "Apache");
        SKILLS_MAP.put("\\btomcat\\b", "Tomcat");
        SKILLS_MAP.put("\\blinux\\b", "Linux");
        SKILLS_MAP.put("\\bunix\\b", "Unix");
        SKILLS_MAP.put("\\bgit\\b", "Git");
        SKILLS_MAP.put("\\bgithub\\b", "GitHub");
        SKILLS_MAP.put("\\bgitlab\\b", "GitLab");
        SKILLS_MAP.put("\\bbitbucket\\b", "Bitbucket");
        SKILLS_MAP.put("\\bjira\\b", "Jira");
        SKILLS_MAP.put("\\bheroku\\b", "Heroku");
        SKILLS_MAP.put("\\bvercel\\b", "Vercel");
        SKILLS_MAP.put("\\bnetlify\\b", "Netlify");

        // Data / ML / AI
        SKILLS_MAP.put("\\bmachine learning\\b", "Machine Learning");
        SKILLS_MAP.put("\\bdeep learning\\b", "Deep Learning");
        SKILLS_MAP.put("\\bartificial intelligence\\b|\\bai\\b", "AI");
        SKILLS_MAP.put("\\bdata science\\b", "Data Science");
        SKILLS_MAP.put("\\bdata analytics?\\b", "Data Analytics");
        SKILLS_MAP.put("\\bnatural language processing\\b|\\bnlp\\b", "NLP");
        SKILLS_MAP.put("\\bcomputer vision\\b", "Computer Vision");
        SKILLS_MAP.put("\\btensorflow\\b", "TensorFlow");
        SKILLS_MAP.put("\\bpytorch\\b", "PyTorch");
        SKILLS_MAP.put("\\bkeras\\b", "Keras");
        SKILLS_MAP.put("\\bscikit[- ]learn\\b|\\bsklearn\\b", "Scikit-learn");
        SKILLS_MAP.put("\\bpandas\\b", "Pandas");
        SKILLS_MAP.put("\\bnumpy\\b", "NumPy");
        SKILLS_MAP.put("\\bmatplotlib\\b", "Matplotlib");
        SKILLS_MAP.put("\\btableau\\b", "Tableau");
        SKILLS_MAP.put("\\bpower\\s*bi\\b", "Power BI");
        SKILLS_MAP.put("\\bhadoop\\b", "Hadoop");
        SKILLS_MAP.put("\\bspark\\b|\\bapache spark\\b", "Apache Spark");
        SKILLS_MAP.put("\\bopencv\\b", "OpenCV");

        // Mobile
        SKILLS_MAP.put("\\bflutter\\b", "Flutter");
        SKILLS_MAP.put("\\breact native\\b", "React Native");
        SKILLS_MAP.put("\\bandroid\\b", "Android");
        SKILLS_MAP.put("\\bios\\b(?=\\s*(?:development|app|developer))?", "iOS");
        SKILLS_MAP.put("\\bxamarin\\b", "Xamarin");

        // Testing
        SKILLS_MAP.put("\\bjunit\\b", "JUnit");
        SKILLS_MAP.put("\\bselenium\\b", "Selenium");
        SKILLS_MAP.put("\\btestng\\b", "TestNG");
        SKILLS_MAP.put("\\bjest\\b", "Jest");
        SKILLS_MAP.put("\\bmocha\\b", "Mocha");
        SKILLS_MAP.put("\\bcypress\\b", "Cypress");
        SKILLS_MAP.put("\\bpostman\\b", "Postman");
        SKILLS_MAP.put("\\bswagger\\b", "Swagger");

        // Other Tools
        SKILLS_MAP.put("\\bmaven\\b", "Maven");
        SKILLS_MAP.put("\\bgradle\\b", "Gradle");
        SKILLS_MAP.put("\\bnpm\\b", "npm");
        SKILLS_MAP.put("\\bwebpack\\b", "Webpack");
        SKILLS_MAP.put("\\bvite\\b", "Vite");
        SKILLS_MAP.put("\\bfigma\\b", "Figma");
        SKILLS_MAP.put("\\bvs\\s*code\\b", "VS Code");
        SKILLS_MAP.put("\\bintelij\\b|\\bintellij\\b|\\bintelij idea\\b", "IntelliJ IDEA");
        SKILLS_MAP.put("\\beclipse\\b", "Eclipse");
        SKILLS_MAP.put("\\bms\\s*office\\b|\\bmicrosoft office\\b", "MS Office");
        SKILLS_MAP.put("\\bms\\s*excel\\b|\\bmicrosoft excel\\b", "MS Excel");
        SKILLS_MAP.put("\\bms\\s*word\\b", "MS Word");
        SKILLS_MAP.put("\\bauto\\s*cad\\b|\\bautocad\\b", "AutoCAD");
        SKILLS_MAP.put("\\bsolidworks\\b", "SolidWorks");
        SKILLS_MAP.put("\\bcreo\\b", "Creo");
        SKILLS_MAP.put("\\bcatia\\b", "CATIA");
        SKILLS_MAP.put("\\bansys\\b", "ANSYS");
        SKILLS_MAP.put("\\barduino\\b", "Arduino");
        SKILLS_MAP.put("\\braspberry pi\\b", "Raspberry Pi");
        SKILLS_MAP.put("\\biot\\b", "IoT");
        SKILLS_MAP.put("\\bblockchain\\b", "Blockchain");
        SKILLS_MAP.put("\\bcyber\\s*security\\b", "Cyber Security");
        SKILLS_MAP.put("\\bagile\\b", "Agile");
        SKILLS_MAP.put("\\bscrum\\b", "Scrum");
        SKILLS_MAP.put("\\boops\\b|\\bobject oriented\\b", "OOP");
        SKILLS_MAP.put("\\bdata structures\\b", "Data Structures");
        SKILLS_MAP.put("\\balgorithms\\b", "Algorithms");
    }

    // ═══════════════════════════════════════
    // JOB ROLE PATTERNS — ordered by specificity
    // ═══════════════════════════════════════
    private static final LinkedHashMap<Pattern, String> JOB_ROLE_PATTERNS = new LinkedHashMap<>();

    static {
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bfull[- ]?stack\\s*(?:web\\s*)?developer\\b", Pattern.CASE_INSENSITIVE), "Full Stack Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bfull[- ]?stack\\s*(?:web\\s*)?engineer\\b", Pattern.CASE_INSENSITIVE), "Full Stack Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bfront[- ]?end\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Frontend Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bback[- ]?end\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Backend Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bdata\\s*scientist\\b", Pattern.CASE_INSENSITIVE), "Data Scientist");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bdata\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "Data Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bdata\\s*analyst\\b", Pattern.CASE_INSENSITIVE), "Data Analyst");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bml\\s*engineer\\b|\\bmachine\\s*learning\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "ML Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bdevops\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "DevOps Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bcloud\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "Cloud Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bsoftware\\s*(?:development\\s*)?engineer\\b", Pattern.CASE_INSENSITIVE), "Software Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bsoftware\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Software Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bweb\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Web Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bjava\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Java Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bpython\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Python Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\breact\\s*developer\\b", Pattern.CASE_INSENSITIVE), "React Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bandroid\\s*developer\\b", Pattern.CASE_INSENSITIVE), "Android Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bios\\s*developer\\b", Pattern.CASE_INSENSITIVE), "iOS Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bmobile\\s*(?:app\\s*)?developer\\b", Pattern.CASE_INSENSITIVE), "Mobile Developer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bui/?ux\\s*(?:designer|developer)\\b", Pattern.CASE_INSENSITIVE), "UI/UX Designer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bqa\\s*(?:engineer|analyst|tester)\\b|\\btest\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "QA Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bproject\\s*manager\\b", Pattern.CASE_INSENSITIVE), "Project Manager");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bbusiness\\s*analyst\\b", Pattern.CASE_INSENSITIVE), "Business Analyst");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bsystem\\s*administrator\\b|\\bsys\\s*admin\\b", Pattern.CASE_INSENSITIVE), "System Administrator");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bdatabase\\s*administrator\\b|\\bdba\\b", Pattern.CASE_INSENSITIVE), "Database Administrator");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bnetwork\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "Network Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bmechanical\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "Mechanical Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\bcivil\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "Civil Engineer");
        JOB_ROLE_PATTERNS.put(Pattern.compile("\\belectrical\\s*engineer\\b", Pattern.CASE_INSENSITIVE), "Electrical Engineer");
    }

    // ═══════════════════════════════════════
    // EDUCATION PATTERNS — strict boundaries to avoid false matches
    // ═══════════════════════════════════════
    private static final LinkedHashMap<Pattern, String> EDUCATION_PATTERNS = new LinkedHashMap<>();

    static {
        EDUCATION_PATTERNS.put(Pattern.compile("\\bph\\.?\\s*d\\b|\\bdoctorate\\b", Pattern.CASE_INSENSITIVE), "Ph.D");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bm\\.?\\s*tech\\b|\\bmaster\\s*of\\s*tech", Pattern.CASE_INSENSITIVE), "M.Tech");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bm\\.e\\.?(?=\\s|$|,|\\()|\\bmaster\\s*of\\s*engineering\\b", Pattern.CASE_INSENSITIVE), "M.E");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bm\\.s\\.?(?=\\s|$|,)|\\bmaster\\s*of\\s*science\\b", Pattern.CASE_INSENSITIVE), "M.S");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bm\\.?\\s*b\\.?\\s*a\\b|\\bmaster\\s*of\\s*business", Pattern.CASE_INSENSITIVE), "MBA");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bm\\.?\\s*c\\.?\\s*a\\b|\\bmaster\\s*of\\s*computer\\s*application", Pattern.CASE_INSENSITIVE), "MCA");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bm\\.?\\s*sc\\b|\\bmsc\\b", Pattern.CASE_INSENSITIVE), "M.Sc");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.?\\s*tech\\b|\\bbachelor\\s*of\\s*tech", Pattern.CASE_INSENSITIVE), "B.Tech");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.e\\.?(?=\\s|$|,|\\()|\\bbachelor\\s*of\\s*engineering\\b", Pattern.CASE_INSENSITIVE), "B.E");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.?\\s*sc\\b|\\bbsc\\b|\\bbachelor\\s*of\\s*science\\b", Pattern.CASE_INSENSITIVE), "B.Sc");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.?\\s*c\\.?\\s*a\\b|\\bbca\\b|\\bbachelor\\s*of\\s*computer\\s*application", Pattern.CASE_INSENSITIVE), "BCA");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.?\\s*b\\.?\\s*a\\b|\\bbba\\b|\\bbachelor\\s*of\\s*business", Pattern.CASE_INSENSITIVE), "BBA");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.?\\s*com\\b|\\bbcom\\b|\\bbachelor\\s*of\\s*commerce", Pattern.CASE_INSENSITIVE), "B.Com");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bb\\.a\\.?(?=\\s|$|,)|\\bbachelor\\s*of\\s*arts\\b", Pattern.CASE_INSENSITIVE), "B.A");
        EDUCATION_PATTERNS.put(Pattern.compile("\\bdiploma\\b", Pattern.CASE_INSENSITIVE), "Diploma");
    }

    // ═══════════════════════════════════════
    // TEXT EXTRACTION
    // ═══════════════════════════════════════
    public String extractText(File file) {
        try {
            return TIKA.parseToString(file);
        } catch (Exception e) {
            throw new RuntimeException("Tika failed: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════
    // NAME EXTRACTION — OpenNLP NER + heuristic fallback
    // ═══════════════════════════════════════

    /**
     * Extract candidate name using a hybrid approach:
     *  1. OpenNLP NER (PERSON entity) — ML-based, high accuracy
     *  2. Heuristic fallback — regex/scoring-based for edge cases
     *
     * synchronized because NameFinderME is NOT thread-safe.
     */
    public synchronized String extractName(String text) {
        if (text == null || text.isBlank()) return "Unknown";

        // ── Strategy 1: OpenNLP NER ──
        String nlpName = extractNameWithOpenNLP(text);
        if (nlpName != null && !nlpName.equals("Unknown")) {
            log.info("NAME_NLP | detected={}", nlpName);
            return nlpName;
        }

        // ── Strategy 2: Heuristic fallback ──
        String heuristicName = extractNameWithHeuristic(text);
        log.info("NAME_HEURISTIC | detected={}", heuristicName);
        return heuristicName;
    }

    /**
     * Use OpenNLP NameFinderME to detect PERSON entities.
     * Scans the first 15 lines of the resume (name is usually near the top).
     */
    private String extractNameWithOpenNLP(String text) {
        try {
            String[] lines = text.split("\\r?\\n");
            int linesToScan = Math.min(15, lines.length);

            for (int i = 0; i < linesToScan; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.length() > 100) continue;

                // Skip lines that are obviously not names
                String lineLower = line.toLowerCase();
                if (lineLower.matches(".*(@|https?://|www\\.|\\d{6,}|phone|email|address).*")) continue;
                if (SECTION_PATTERN.matcher(line).matches()) continue;

                // Tokenize with OpenNLP SimpleTokenizer
                String[] tokens = SimpleTokenizer.INSTANCE.tokenize(line);
                if (tokens.length < 2 || tokens.length > 10) continue;

                // Find PERSON entities
                Span[] nameSpans = nameFinder.find(tokens);

                if (nameSpans.length > 0) {
                    // Extract the full name from the first PERSON span
                    Span span = nameSpans[0];
                    StringBuilder fullName = new StringBuilder();
                    for (int j = span.getStart(); j < span.getEnd(); j++) {
                        if (fullName.length() > 0) fullName.append(" ");
                        fullName.append(tokens[j]);
                    }

                    String name = fullName.toString().trim();

                    // Validate: at least 2 chars, mostly alphabetic, not a noise word
                    if (name.length() >= 2
                            && name.replaceAll("[^a-zA-Z]", "").length() >= 2
                            && !NOISE_WORDS.contains(name.toLowerCase())) {
                        nameFinder.clearAdaptiveData();
                        return formatTitleCase(name);
                    }
                }
            }

            nameFinder.clearAdaptiveData();
        } catch (Exception e) {
            log.warn("OpenNLP name extraction failed, falling back to heuristic: {}", e.getMessage());
            try { nameFinder.clearAdaptiveData(); } catch (Exception ignored) {}
        }
        return null;
    }

    // ═══════════════════════════════════════
    // HEURISTIC NAME EXTRACTION (fallback)
    // ═══════════════════════════════════════
    private static final Set<String> COMMON_FIRST_NAMES = Set.of(
            "john","jane","james","mary","robert","patricia","michael","linda","william","elizabeth",
            "david","barbara","richard","susan","joseph","jessica","thomas","sarah","charles","karen",
            "rahul","priya","amit","pooja","suresh","ananya","vikram","neha","arjun","kavya",
            "mohammed","fatima","ahmed","aisha","ali","zainab","hassan","maryam","ibrahim","nour",
            "wei","li","zhang","wang","chen","liu","yang","huang","zhao","wu",
            "maria","carlos","ana","luis","jose","sofia","miguel","isabella","diego","valentina"
    );

    private static final Set<String> COMMON_LAST_NAMES = Set.of(
            "smith","johnson","williams","brown","jones","garcia","miller","davis","rodriguez","martinez",
            "sharma","singh","patel","kumar","verma","gupta","reddy","naik","desai","iyer",
            "khan","ahmed","ali","hassan","malik","hussain","rahman","fatima","siddiqui","ansari",
            "wang","li","zhang","chen","liu","yang","huang","zhao","wu","zhou",
            "lopez","gonzalez","hernandez","martin","jackson","thompson","white","lee","walker","hall"
    );

    private static final Set<String> HEURISTIC_SECTION_HEADERS = Set.of(
            "objective","summary","profile","about","education","qualification","academic","experience",
            "employment","work","skills","technical","core","competencies","strengths","projects",
            "certifications","certificates","achievements","awards","publications","papers","languages",
            "hobbies","interests","references","declaration","contact","address","personal","details",
            "information","resume","cv","curriculum","vitae","bachelor","master","degree","university",
            "college","institute","school","passing","year","cgpa","gpa","percentage","marks","score",
            "duration","location","company","organization","role","responsibilities","technologies",
            "tools","frameworks","methodologies","certification","training","internship","volunteer"
    );

    private static final Set<String> HEURISTIC_JOB_TITLES = Set.of(
            "engineer","developer","programmer","analyst","manager","lead","senior","junior","intern",
            "trainee","consultant","specialist","coordinator","executive","director","officer","administrator",
            "assistant","associate","head","chief","founder","owner","student","graduate","undergraduate",
            "researcher","scientist","architect","designer","tester","devops","fullstack","backend",
            "frontend","mobile","web","data","machine","learning","ai","cloud","security","network"
    );

    private static final Set<String> NAME_PREFIXES = Set.of(
            "mr","mrs","ms","miss","dr","prof","professor","er","shri","smt"
    );

    private static final Pattern HEURISTIC_EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern HEURISTIC_PHONE = Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b");
    private static final Pattern HEURISTIC_URL = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern HEURISTIC_ADDRESS = Pattern.compile(".*\\b(st|street|rd|road|ave|avenue|nagar|colony|puram|village|town|district|pin|postcode|state|landmark|near|opposite|block|sector|area|locality|taluk|tk|dt|po|flat|no|floor|building|phase)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEURISTIC_DATE = Pattern.compile("\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+\\d{4}|\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\b(?:20\\d{2}|19\\d{2})\\b");
    private static final Pattern HEURISTIC_NAME_LABEL = Pattern.compile("^(?:name|candidate|applicant|full\\s*name)\\s*[:;\\-]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private String extractNameWithHeuristic(String text) {
        if (text == null || text.isBlank()) return "Unknown";

        String[] lines = text.split("\\r?\\n");
        List<HeuristicNameCandidate> candidates = new ArrayList<>();

        // Strategy 1: Explicit "Name:" label (highest priority)
        for (int i = 0; i < Math.min(25, lines.length); i++) {
            String line = lines[i].trim();
            if (line.length() < 3 || line.length() > 70) continue;

            Matcher m = HEURISTIC_NAME_LABEL.matcher(line);
            if (m.find()) {
                String raw = m.group(1).trim();
                HeuristicNameCandidate nc = evaluateNameCandidate(raw, i, true);
                if (nc != null && nc.confidence >= 95) {
                    return nc.formattedName;
                }
                if (nc != null) candidates.add(nc);
            }
        }

        // Strategy 2: Top-of-resume heuristic (lines 0-10)
        for (int i = 0; i < Math.min(12, lines.length); i++) {
            String line = lines[i].trim();
            if (line.length() < 3 || line.length() > 70) continue;
            if (HEURISTIC_EMAIL.matcher(line).find()) continue;
            if (HEURISTIC_PHONE.matcher(line).find()) continue;
            if (HEURISTIC_URL.matcher(line).find()) continue;
            if (HEURISTIC_ADDRESS.matcher(line).matches()) continue;
            if (HEURISTIC_DATE.matcher(line).find()) continue;
            if (line.matches(".*[|\u2022\u25CF\u25BA\u2192\u2550\u2500=].*")) continue;

            HeuristicNameCandidate nc = evaluateNameCandidate(line, i, false);
            if (nc != null) candidates.add(nc);
        }

        // Strategy 3: Pattern-based search in first 30 lines
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            String line = lines[i].trim();
            if (line.matches("^([A-Z][a-z]+[.]?\\s+){1,4}[A-Z][a-z]+[.]?$") && !line.toLowerCase().matches(".*:\\s*$")) {
                HeuristicNameCandidate nc = evaluateNameCandidate(line, i, false);
                if (nc != null) candidates.add(nc);
            }
            if (line.matches("^[A-Z][A-Z.\\-\\s']{3,50}$") && line.split("\\s+").length <= 5) {
                HeuristicNameCandidate nc = evaluateNameCandidate(line, i, false);
                if (nc != null) candidates.add(nc);
            }
        }

        return candidates.stream()
                .filter(c -> c.confidence >= 85)
                .max(Comparator.comparingInt(c -> c.confidence))
                .map(c -> c.formattedName)
                .orElse("Unknown");
    }

    private static class HeuristicNameCandidate {
        final String formattedName;
        final int confidence;
        final int lineIndex;
        HeuristicNameCandidate(String formatted, int conf, int idx) {
            this.formattedName = formatted;
            this.confidence = conf;
            this.lineIndex = idx;
        }
    }

    private HeuristicNameCandidate evaluateNameCandidate(String raw, int lineIndex, boolean fromLabel) {
        if (raw == null || raw.isBlank()) return null;

        String cleaned = raw.replaceAll("\\s*\\([^)]*[@\\d][^)]*\\)\\s*", " ");
        cleaned = cleaned.replaceAll("\\s*[|\u2022\u25CF\u25BA\u2192\\-|].*", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z.\\-\\s']", " ").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        if (cleaned.length() < 3 || cleaned.length() > 60) return null;
        String lower = cleaned.toLowerCase();

        if (HEURISTIC_SECTION_HEADERS.contains(lower)) return null;
        for (String s : HEURISTIC_SECTION_HEADERS) if (lower.contains(s)) return null;

        for (String t : HEURISTIC_JOB_TITLES) {
            if (lower.matches(".*\\b" + Pattern.quote(t) + "\\b.*")) {
                String[] words = lower.split("\\s+");
                if (words.length <= 4) return null;
            }
        }

        if (lower.matches(".*\\b(resume|cv|bachelor|master|degree|university|college|institute|passing|cgpa|gpa|percentage|marks|score|duration)\\b.*")) return null;

        String evalName = cleaned;
        for (String p : NAME_PREFIXES) {
            if (lower.startsWith(p + ".") || lower.startsWith(p + " ")) {
                evalName = cleaned.replaceFirst("(?i)^" + Pattern.quote(p) + "\\.?\\s*", "").trim();
                lower = evalName.toLowerCase();
                break;
            }
        }
        if (evalName.length() < 3) return null;

        String[] words = evalName.trim().split("\\s+");
        boolean hasCommonName = false;
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (!clean.isEmpty() && (COMMON_FIRST_NAMES.contains(clean) || COMMON_LAST_NAMES.contains(clean))) {
                hasCommonName = true;
                break;
            }
        }

        boolean hasCapitalStart = false, hasValidLength = true;
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (Character.isUpperCase(w.charAt(0)) || w.matches("^[A-Z.]+$")) hasCapitalStart = true;
            String clean = w.replaceAll("[^a-zA-Z]", "");
            if (!clean.isEmpty() && clean.length() > 25) hasValidLength = false;
        }
        if (!hasCapitalStart || !hasValidLength) return null;

        int score = fromLabel ? 90 : 50;

        if (lineIndex <= 2) score += 20;
        else if (lineIndex <= 5) score += 15;
        else if (lineIndex <= 10) score += 8;

        if (words.length >= 2 && words.length <= 4) score += 15;
        else if (words.length <= 6) score += 5;
        else score -= 20;

        if (hasCommonName) score += 25;

        int initialCount = 0;
        for (String w : words) {
            if (w.matches("^[A-Z]\\.$") || (w.length() == 1 && w.matches("[A-Z]"))) initialCount++;
        }
        if (initialCount > 0 && initialCount <= 3) score += 10;

        if (evalName.matches("^[A-Z.\\-\\s']+$") && evalName.replaceAll("[^A-Z]", "").length() >= 3) score += 8;

        if (lower.matches(".*\\d.*")) score -= 50;
        if (evalName.replaceAll("[^a-zA-Z]", "").length() < 4) score -= 30;

        score = Math.min(100, Math.max(0, score));
        if (score < 70) return null;

        return new HeuristicNameCandidate(formatTitleCase(evalName), score, lineIndex);
    }


    // ═══════════════════════════════════════
    // EMAIL
    // ═══════════════════════════════════════
    public String extractEmail(String text) {
        if (text == null) return null;
        Matcher m = EMAIL_PATTERN.matcher(text);
        List<String> emails = new ArrayList<>();
        while (m.find()) {
            emails.add(m.group().toLowerCase().trim());
        }
        // Return first non-generic email, or first email
        for (String e : emails) {
            if (!e.contains("applicant@") && !e.contains("example@") && !e.contains("sample@")
                    && !e.contains("test@") && !e.contains("noreply@")) {
                return e;
            }
        }
        return emails.isEmpty() ? null : emails.get(0);
    }

    // ═══════════════════════════════════════
    // PHONE — enhanced to avoid register numbers
    // ═══════════════════════════════════════
    public String extractPhone(String text) {
        if (text == null) return null;
        Matcher m = PHONE_PATTERN.matcher(text);
        while (m.find()) {
            String phone = m.group().trim();
            // Validate: must be exactly 10 digits (after removing +91 prefix)
            String digitsOnly = phone.replaceAll("[^\\d]", "");
            if (digitsOnly.length() == 10 || (digitsOnly.startsWith("91") && digitsOnly.length() == 12)) {
                // Verify nearby text doesn't say "register", "roll", "enrollment"
                int start = Math.max(0, m.start() - 30);
                int end = Math.min(text.length(), m.end() + 10);
                String context = text.substring(start, end).toLowerCase();
                if (!context.contains("register") && !context.contains("roll")
                        && !context.contains("enrollment") && !context.contains("enrolment")
                        && !context.contains("reg no") && !context.contains("reg.no")) {
                    return phone;
                }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════
    // SKILLS — regex-based matching (150+ skills)
    // ═══════════════════════════════════════
    public String extractSkills(String text) {
        if (text == null) return "Not Specified";

        Set<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase();

        for (Map.Entry<String, String> entry : SKILLS_MAP.entrySet()) {
            Pattern p = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            if (p.matcher(lower).find()) {
                found.add(entry.getValue());
            }
        }

        // Deduplicate: if "Spring Boot" is found, remove standalone "Spring"
        if (found.contains("Spring Boot")) found.remove("Spring");
        if (found.contains("React Native")) found.remove("React");
        if (found.contains("Node.js")) found.remove("Express.js"); // keep both actually
        if (found.contains("Tailwind CSS")) found.remove("CSS");

        return found.isEmpty() ? "Not Specified" : String.join(", ", found);
    }

    // ═══════════════════════════════════════
    // EDUCATION — section-aware + degree-specific extraction
    // ═══════════════════════════════════════
    public String extractEducation(String text) {
        if (text == null) return "Not Specified";

        // --- Step 1: Find all degrees mentioned ---
        List<String> found = new ArrayList<>();
        for (Map.Entry<Pattern, String> entry : EDUCATION_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(text).find()) {
                if (!found.contains(entry.getValue())) {
                    found.add(entry.getValue());
                }
            }
        }

        // --- Step 2: For each degree, try to find specialization on the same line ---
        if (!found.isEmpty()) {
            String[] lines = text.split("\\n");
            for (int i = 0; i < found.size(); i++) {
                String degree = found.get(i);
                String degreeKey = degree.replace(".", "\\.?").toLowerCase();

                for (String line : lines) {
                    String lineLower = line.toLowerCase().trim();
                    if (lineLower.contains(degree.toLowerCase().replace(".", "")) ||
                            lineLower.matches(".*\\b" + degreeKey + "\\b.*")) {

                        // ✅ Final Code: Extract Specialization for ANY Degree
// Works with: B.Tech, M.Tech, B.Sc, M.Sc, B.E, M.B.A, etc.

                        String normalizedDegree = degree.replaceAll("\\.", "").trim().toUpperCase();

// 🔹 Regex: Match ANY degree + optional specialization
// Uses Pattern.quote to safely handle special chars in degree (e.g., "B.Tech", "M.B.A")
                        Pattern specInLine = Pattern.compile(
                                "(?:" + Pattern.quote(degree) + ")\\s*(?:\\.|\\s)?\\s*(?:in|\\-|–|\\(|at)\\s*" +
                                        "([A-Za-z][A-Za-z .&/]{2,})",   // Allow &, / for fields like "AI & ML"
                                Pattern.CASE_INSENSITIVE);

                        Matcher specM = specInLine.matcher(line);

                        if (specM.find()) {
                            String spec = specM.group(1).trim()
                                    // Clean leading/trailing punctuation & whitespace
                                    .replaceAll("^[\\-–\\s\\(\\)]+", "")
                                    .replaceAll("[\\-–\\s\\(\\)]+$", "")
                                    // Normalize multiple spaces
                                    .replaceAll("\\s{2,}", " ")
                                    // Remove trailing junk keywords
                                    .replaceAll("\\b(from|at|cgpa|gpa|percentage|marks|year|university|college|of|the|with|during).*", "")
                                    .trim();

                            // Remove trailing single-letter words (e.g., "Computer Science A" → "Computer Science")
                            String[] words = spec.split("\\s+");
                            if (words.length > 1 && words[words.length - 1].length() == 1) {
                                spec = String.join(" ", Arrays.copyOf(words, words.length - 1)).trim();
                            }

                            // Final validation & standardized output
                            if (spec.length() > 2 && spec.length() < 80) {
                                found.set(i, degree + " in " + spec);
                            } else {
                                found.set(i, degree); // fallback: degree only
                            }
                        } else {
                            // No specialization found — keep original degree label
                            found.set(i, degree);
                        }


                        // Try to extract CGPA/percentage from same line
                        Pattern cgpa = Pattern.compile(
                                "(?:cgpa|gpa|percentage|aggregate|marks?)\\s*(?::|–|-|of)?\\s*([\\d.]+)\\s*(?:%|/10|/4)?",
                                Pattern.CASE_INSENSITIVE);
                        Matcher cgpaM = cgpa.matcher(line);
                        if (cgpaM.find()) {
                            String score = cgpaM.group(1).trim();
                            try {
                                double val = Double.parseDouble(score);
                                if (val > 0 && val <= 100) {
                                    String label = val <= 10 ? "CGPA" : "%";
                                    found.set(i, found.get(i) + " (" + label + ": " + score + ")");
                                }
                            } catch (NumberFormatException ignored) {}
                        }

                        // Try to extract year from same line
                        Pattern yearP = Pattern.compile("\\b(20\\d{2})\\b");
                        Matcher yearM = yearP.matcher(line);
                        List<String> years = new ArrayList<>();
                        while (yearM.find()) years.add(yearM.group(1));
                        if (!years.isEmpty() && !found.get(i).contains("20")) {
                            if (years.size() >= 2) {
                                found.set(i, found.get(i) + " [" + years.get(0) + "-" + years.get(years.size()-1) + "]");
                            } else {
                                found.set(i, found.get(i) + " [" + years.get(0) + "]");
                            }
                        }

                        break; // only use first matching line per degree
                    }
                }
            }
        }

        // --- Step 3: Fallback CGPA if none found per-degree ---
        if (!found.isEmpty() && !found.get(0).contains("CGPA") && !found.get(0).contains("%")) {
            Pattern cgpaFallback = Pattern.compile(
                    "(?:cgpa|gpa)\\s*(?::|–|-)?\\s*([\\d.]+)\\s*(?:/10|/4)?",
                    Pattern.CASE_INSENSITIVE);
            Matcher cgpaFM = cgpaFallback.matcher(text);
            if (cgpaFM.find()) {
                String score = cgpaFM.group(1).trim();
                try {
                    double val = Double.parseDouble(score);
                    if (val > 0 && val <= 10) {
                        found.set(0, found.get(0) + " (CGPA: " + score + ")");
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return found.isEmpty() ? "Not Specified" : String.join(", ", found);
    }

    // ═══════════════════════════════════════
    // EXPERIENCE — multi-strategy extraction
    // ═══════════════════════════════════════
    public String extractExperience(String text) {
        if (text == null) return "Fresher";

        String lower = text.toLowerCase();

        // --- Strategy 1: Explicit "X years" or "X+ years" or "X years Y months" ---
        Matcher m = EXPERIENCE_YEARS_PATTERN.matcher(text);
        if (m.find()) {
            String years = m.group(1);
            String months = m.group(2);
            int y = Integer.parseInt(years);
            if (y >= 0 && y <= 50) { // sanity check
                StringBuilder sb = new StringBuilder(y + " year" + (y != 1 ? "s" : ""));
                if (months != null && !months.isEmpty()) {
                    int mo = Integer.parseInt(months);
                    if (mo > 0 && mo < 12) {
                        sb.append(" ").append(mo).append(" month").append(mo != 1 ? "s" : "");
                    }
                }
                return sb.toString();
            }
        }

        // --- Strategy 2: "X months experience/exp" ---
        Pattern monthsExpPattern = Pattern.compile(
                "(\\d{1,2})\\+?\\s*(?:months?)\\s*(?:of\\s*)?(?:experience|exp|work)",
                Pattern.CASE_INSENSITIVE);
        Matcher monthsM = monthsExpPattern.matcher(text);
        if (monthsM.find()) {
            int mo = Integer.parseInt(monthsM.group(1));
            if (mo > 0 && mo <= 24) {
                if (mo >= 12) {
                    return (mo / 12) + " year" + (mo / 12 != 1 ? "s" : "") +
                            (mo % 12 > 0 ? " " + (mo % 12) + " month" + (mo % 12 != 1 ? "s" : "") : "");
                }
                return mo + " month" + (mo != 1 ? "s" : "");
            }
        }

        // --- Strategy 3: Date range calculation ("Jan 2020 - Present" or "Mar 2019 - Dec 2022") ---
        Pattern dateRangePattern = Pattern.compile(
                "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s*['\'']?\\s*(\\d{2,4})" +
                        "\\s*(?:–|-|to|\\u2013|\\u2014)\\s*" +
                        "(?:(present|current|till\\s*date|ongoing)|" +
                        "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s*['\'']?\\s*(\\d{2,4}))",
                Pattern.CASE_INSENSITIVE);
        Matcher dateM = dateRangePattern.matcher(text);
        int totalMonths = 0;
        boolean foundDateRange = false;
        while (dateM.find()) {
            int startYear = parseYear(dateM.group(1));
            int endYear;
            if (dateM.group(2) != null) { // "Present"
                endYear = 2026; // current year
            } else {
                endYear = parseYear(dateM.group(3));
            }
            if (startYear > 1990 && endYear >= startYear && endYear <= 2030) {
                totalMonths += (endYear - startYear) * 12;
                foundDateRange = true;
            }
        }

        // --- Strategy 4: Year spans ("2019 - 2023" or "2019 – Present") in experience section ---
        if (!foundDateRange) {
            Pattern yearSpanPattern = Pattern.compile(
                    "\\b(20\\d{2})\\s*(?:–|-|to|\\u2013|\\u2014)\\s*" +
                            "(?:(present|current|till\\s*date|ongoing)|(20\\d{2}))\\b",
                    Pattern.CASE_INSENSITIVE);

            // Only search in experience-related sections
            String expSection = extractSection(text, "experience|work|employment|professional|career|company");
            if (expSection != null && !expSection.isEmpty()) {
                Matcher ysM = yearSpanPattern.matcher(expSection);
                while (ysM.find()) {
                    int sy = Integer.parseInt(ysM.group(1));
                    int ey;
                    if (ysM.group(2) != null) {
                        ey = 2026;
                    } else {
                        ey = Integer.parseInt(ysM.group(3));
                    }
                    if (sy > 1990 && ey >= sy && ey <= 2030) {
                        totalMonths += (ey - sy) * 12;
                        foundDateRange = true;
                    }
                }
            }
        }

        if (foundDateRange && totalMonths > 0) {
            int years = totalMonths / 12;
            int months = totalMonths % 12;
            StringBuilder sb = new StringBuilder();
            if (years > 0) {
                sb.append(years).append(" year").append(years != 1 ? "s" : "");
            }
            if (months > 0) {
                if (years > 0) sb.append(" ");
                sb.append(months).append(" month").append(months != 1 ? "s" : "");
            }
            return sb.toString();
        }

        // --- Strategy 5: Keyword-based detection ---
        if (lower.contains("fresher") || lower.contains("entry level") || lower.contains("entry-level")
                || lower.contains("new graduate") || lower.contains("fresh graduate")) {
            return "Fresher";
        }

        if (lower.contains("internship") || lower.contains("intern ") || lower.contains("trainee")) {
            return "Intern";
        }

        return "Fresher";
    }

    /**
     * Extract a section of text by header keyword.
     */
    private String extractSection(String text, String headerKeywords) {
        String[] lines = text.split("\\n");
        Pattern sectionStart = Pattern.compile(
                "^\\s*(?:#+\\s*)?(?:" + headerKeywords + ").*$",
                Pattern.CASE_INSENSITIVE);
        StringBuilder section = new StringBuilder();
        boolean inSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (sectionStart.matcher(trimmed).matches() && trimmed.length() < 50) {
                inSection = true;
                continue;
            }
            if (inSection) {
                // End on next section header
                if (SECTION_PATTERN.matcher(trimmed).matches()) {
                    break;
                }
                section.append(line).append("\n");
            }
        }
        return section.toString();
    }

    /**
     * Parse a 2 or 4 digit year into 4-digit year.
     */
    private int parseYear(String yearStr) {
        if (yearStr == null) return 0;
        int y = Integer.parseInt(yearStr.trim());
        if (y < 100) {
            y += (y > 50) ? 1900 : 2000;
        }
        return y;
    }

    // ═══════════════════════════════════════
    // JOB ROLE — pattern-based matching
    // ═══════════════════════════════════════
    public String extractJobRole(String text) {
        if (text == null) return "Unspecified Role";

        // First check explicit role patterns in first 20 lines (likely to be the headline)
        String[] lines = text.split("\\n");
        for (int i = 0; i < Math.min(20, lines.length); i++) {
            String line = lines[i].trim();
            for (Map.Entry<Pattern, String> entry : JOB_ROLE_PATTERNS.entrySet()) {
                if (entry.getKey().matcher(line).find()) {
                    return entry.getValue();
                }
            }
        }

        // Then check entire text
        for (Map.Entry<Pattern, String> entry : JOB_ROLE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(text).find()) {
                return entry.getValue();
            }
        }

        // Fallback: infer from dominant skills
        String skills = extractSkills(text).toLowerCase();
        if (skills.contains("react") || skills.contains("angular") || skills.contains("vue"))
            return "Frontend Developer";
        if (skills.contains("spring boot") || skills.contains("node.js") || skills.contains("django"))
            return "Backend Developer";
        if (skills.contains("machine learning") || skills.contains("tensorflow") || skills.contains("pytorch"))
            return "ML Engineer";
        if (skills.contains("data science") || skills.contains("pandas") || skills.contains("tableau"))
            return "Data Analyst";
        if (skills.contains("docker") || skills.contains("kubernetes") || skills.contains("aws"))
            return "DevOps Engineer";
        if (skills.contains("flutter") || skills.contains("react native") || skills.contains("android"))
            return "Mobile Developer";
        if (skills.contains("autocad") || skills.contains("solidworks") || skills.contains("creo"))
            return "Mechanical Engineer";

        return "Unspecified Role";
    }

    // ═══════════════════════════════════════
    // LOCATION — comprehensive
    // ═══════════════════════════════════════
    public String extractLocation(String text) {
        if (text == null) return "Not Specified";
        String lower = text.toLowerCase();

        // Check first 30 lines for address-related location
        String[] lines = text.split("\\n");
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            String line = lines[i].toLowerCase().trim();
            for (Map.Entry<String, String> entry : LOCATION_MAP.entrySet()) {
                if (line.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // Fallback: check entire text
        for (Map.Entry<String, String> entry : LOCATION_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Try PIN code extraction for Indian locations
        Pattern pinPattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher pm = pinPattern.matcher(text);
        if (pm.find()) {
            String pin = pm.group(1);
            if (pin.startsWith("6") || pin.startsWith("5") || pin.startsWith("4")
                    || pin.startsWith("3") || pin.startsWith("1") || pin.startsWith("2")
                    || pin.startsWith("7") || pin.startsWith("8") || pin.startsWith("9")) {
                return "India (PIN: " + pin + ")";
            }
        }

        return "Not Specified";
    }

    // ═══════════════════════════════════════
    // LINKEDIN
    // ═══════════════════════════════════════
    public String extractLinkedIn(String text) {
        if (text == null) return null;
        Matcher m = LINKEDIN_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    // ═══════════════════════════════════════
    // GITHUB
    // ═══════════════════════════════════════
    public String extractGithub(String text) {
        if (text == null) return null;
        Matcher m = GITHUB_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    // ═══════════════════════════════════════
    // CERTIFICATIONS — section-based extraction
    // ═══════════════════════════════════════
    public String extractCertifications(String text) {
        if (text == null) return "None";

        String[] lines = text.split("\\n");
        List<String> certs = new ArrayList<>();
        boolean inCertSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();

            // 🔥 Detect certification section
            if (lower.matches("^\\s*(certif|certification|certifications|achievements?|awards?|licenses?).*$")
                    && trimmed.length() < 50) {
                inCertSection = true;
                continue;
            }

            // 🔥 STOP if soft skills section starts
            if (lower.matches("^\\s*(soft skills|skills|technical skills).*$")) {
                inCertSection = false;
                continue;
            }

            // 🔥 Stop on next major section
            if (inCertSection && SECTION_PATTERN.matcher(trimmed).matches()) {
                inCertSection = false;
                continue;
            }

            if (inCertSection && !trimmed.isEmpty()) {

                // 🔥 Split multiple items in same line (VERY IMPORTANT)
                String[] parts = trimmed.split("\\s*;\\s*");

                for (String part : parts) {
                    String clean = part.trim()
                            .replaceAll("^[•\\-–\\*\\d.]+\\s*", "")   // remove bullets
                            .replaceAll("\\s{2,}", " ")
                            .trim();

                    String lowerPart = clean.toLowerCase();

                    // ❌ Skip soft skill words
                    if (lowerPart.contains("soft skills") ||
                            lowerPart.contains("analytical") ||
                            lowerPart.contains("problem solving") ||
                            lowerPart.length() < 4) {
                        continue;
                    }

                    // ✅ Valid certification
                    if (clean.length() > 4 && clean.length() < 150) {
                        certs.add(clean);
                    }
                }
            }
        }

        // 🔥 GLOBAL fallback detection (if section missing)
        String lowerText = text.toLowerCase();
        String[] knownCerts = {
                "aws certified", "azure certified", "google cloud",
                "ccna", "ccnp", "comptia", "pmp", "scrum master",
                "oracle certified", "istqb", "salesforce",
                "coursera", "udemy", "edx", "nptel", "linkedin learning",
                "cisco", "tcs ion", "infosys springboard", "hp life", "forage"
        };

        for (String cert : knownCerts) {
            if (lowerText.contains(cert) &&
                    certs.stream().noneMatch(c -> c.toLowerCase().contains(cert))) {
                certs.add(cert);
            }
        }

        if (certs.isEmpty()) return "None";

        // ✅ Remove duplicates + keep order
        List<String> uniqueCerts = certs.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        return String.join("; ", uniqueCerts);
    }
    // ═══════════════════════════════════════
    // PORTFOLIO LINK
    // ═══════════════════════════════════════
    public String extractPortfolio(String text) {
        if (text == null) return null;
        Matcher m = PORTFOLIO_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    // ═══════════════════════════════════════
    // LANGUAGES — spoken languages
    // ═══════════════════════════════════════
    public String extractLanguages(String text) {
        if (text == null) return null;

        String[] langs = {"english", "hindi", "tamil", "telugu", "kannada", "malayalam",
                "marathi", "bengali", "gujarati", "punjabi", "urdu", "sanskrit",
                "french", "german", "spanish", "japanese", "chinese", "korean", "arabic"};

        Set<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase();

        for (String lang : langs) {
            if (lower.contains(lang)) {
                found.add(lang.substring(0, 1).toUpperCase() + lang.substring(1));
            }
        }

        return found.isEmpty() ? null : String.join(", ", found);
    }

    // ═══════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════
    private String formatTitleCase(String text) {
        if (text == null || text.isBlank()) return text;
        return Arrays.stream(text.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(word -> {
                    String clean = word.replaceAll("[^a-zA-Z.\\-]", "");
                    if (clean.isEmpty()) return "";
                    return clean.substring(0, 1).toUpperCase() + clean.substring(1).toLowerCase();
                })
                .filter(w -> !w.isEmpty())
                .collect(Collectors.joining(" "));
    }
}