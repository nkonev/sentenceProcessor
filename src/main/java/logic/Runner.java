package logic;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import javax.validation.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Component
public class Runner {
    @Min(1)
    @Value("${nThreads}")
    int nThreads;

    @Min(0)
    @Value("${wordCount}")
    int wordCount;

    @NotEmpty
    @Value("${file}")
    String file;

    @NotEmpty
    @Value("${file-encoding}")
    String fileEncoding;

    @Value("${artifactId}")
    String artifactId;

    @Value("${version}")
    String version;


    static String exit = "q";

    /**
     * сам себе лаунчер :)
     *
     * @param args
     */
    public static void main(String args[]) throws Exception {

        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
                "SpringConfig.xml");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        String isExit = "";
        do {
            // Когда для автоматического поиска бины помечены как компоненты, в
            // методе getBean в качестве имени выступает имя класса с маленькой
            // буквы, по соглашению
            Runner obj = (Runner) context.getBean("runner");

            validate(obj, validator);

            obj.run();

            System.out.println("Enter \"" + exit + "\" to exit, or enter any other to reload properties and re-process file...");

            isExit = bufferedReader.readLine();
            context.refresh();
        } while (!isExit.equals(exit));

        bufferedReader.close();
        context.close();
        factory.close();
    }

    void init() {
    }

    void run() {
        ExecutorService service = Executors.newFixedThreadPool(nThreads);

        try {
            org.joda.time.DateTime startTime = new org.joda.time.DateTime();

            List<String> strings = readSentences(
                    new File(file), fileEncoding);


            // Разделяем предложения на слова
            List<Callable<ResultContainer>> wordSplitTasks = new ArrayList<Callable<ResultContainer>>();

            int i = 0;
            for (final String s : strings) {
                final int iii = i;
                wordSplitTasks.add(new Callable<ResultContainer>() {
                    public ResultContainer call() throws Exception {
                        int count = 0;
                        List<String> a = split2(s);
                        for (int i = 0; i < a.size(); i++) {
                            if (a.get(i) != null) {
                                count++;
                            }
                        }
                        // System.out.println("Обрабатываю \"" + s+ "\" "+count
                        // + "слов");
                        if (count == wordCount) {
                            // Если строка подходит, то возвращаем её
                            return new ResultContainer(s, iii);
                        } else {
                            return new ResultContainer(null, iii);
                        }
                    }
                });
                ++i;
            }

            List<Future<ResultContainer>> futures = service
                    .invokeAll(wordSplitTasks);

            List<ResultContainer> results = new ArrayList<ResultContainer>();

            // В future -- результаты предложения
            for (Future<ResultContainer> frc : futures) {
                ResultContainer rc = frc.get();
                results.add(rc);
            }

            Collections.sort(results);

            // В future -- результаты предложения
            for (ResultContainer rc : results) {
                if (rc.s != null)
                    System.out.println(rc.s);
            }

            org.joda.time.DateTime endTime = new org.joda.time.DateTime();

            System.out.println("\n" + artifactId + " " + version);

            System.out.println("printed sentences with: " + wordCount
                    + " words");
            System.out.println("threads: " + nThreads);
            System.out.println("wasted time: "
                    + (endTime.getMillis() - startTime.getMillis()));
            //

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } finally {
            service.shutdown();
        }
    }

    public static String ruRegex = "[а-яА-Яa-zA-Z]+";

    static List<String> split2(String s) {
        Pattern p = Pattern.compile(ruRegex);
        Matcher m = p.matcher(s);

        List<String> ls = new ArrayList<String>();

        for (; m.find(); ) {
            String u = m.group();
            ls.add(u);
        }
        return ls;
    }

    private static List<String> readSentences(File file, String charset)
            throws IOException {
        InputStream is = new FileInputStream(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(is,
                charset));

        List<String> list = new ArrayList<String>();
        int c = 0;
        StringBuilder sb = new StringBuilder();
        while ((c = br.read()) != -1) {
            char ch = (char) c;
            if (ch == '\r' || ch == '\n')
                continue;
            sb.append(ch);
            if (ch == '.') {
                String s = sb.toString();
                // System.out.println(s);
                list.add(s);
                sb.setLength(0);
            }

        }
        is.close();
        br.close();
        return list;
    }

    void destroy() {
    }

    // http://habrahabr.ru/post/68318/
    public static void validate(Object object, Validator validator) throws ValidationException {
        Set<ConstraintViolation<Object>> constraintViolations = validator
                .validate(object);
        if(constraintViolations.size()!=0){
            System.err.println("Validation erors:");
            for (ConstraintViolation<Object> cv : constraintViolations)
                System.err.println(String.format(
                        "property: [%s], value: [%s], message: [%s]",
                        cv.getPropertyPath(), cv.getInvalidValue(), cv.getMessage()));

            throw new ValidationException();
        }
    }
}

class ResultContainer implements Comparable<ResultContainer> {
	public ResultContainer(String s, int position) {
		super();
		this.s = s;
		this.position = position;
	}

	public String s;
	public int position;

	public int compareTo(ResultContainer o) {
		int compareQuantity = o.position;

		// ascending order
		return position - compareQuantity;
	}
}
