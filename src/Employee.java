
public class Employee {

    private String id;
    private String name;
    private String department;
    private double salary;

    // Constructor: sets employee details
    public Employee(String id, String name,
                    String department, double salary) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.salary = salary;
    }

    // Get employee details
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public double getSalary() {
        return salary;
    }

    // Convert employee details into one CSV row
    public String toCsv() {
        return id + "," + name + "," + department + "," + salary;
    }
}
