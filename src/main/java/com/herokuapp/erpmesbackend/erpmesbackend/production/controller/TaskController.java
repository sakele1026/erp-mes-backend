package com.herokuapp.erpmesbackend.erpmesbackend.production.controller;

import com.herokuapp.erpmesbackend.erpmesbackend.exceptions.NotFoundException;
import com.herokuapp.erpmesbackend.erpmesbackend.production.dto.TaskDTO;
import com.herokuapp.erpmesbackend.erpmesbackend.production.model.Category;
import com.herokuapp.erpmesbackend.erpmesbackend.production.model.Task;
import com.herokuapp.erpmesbackend.erpmesbackend.production.model.Type;
import com.herokuapp.erpmesbackend.erpmesbackend.production.repository.TaskRepository;
import com.herokuapp.erpmesbackend.erpmesbackend.production.request.AssignmentRequest;
import com.herokuapp.erpmesbackend.erpmesbackend.production.request.TaskRequest;
import com.herokuapp.erpmesbackend.erpmesbackend.staff.model.Employee;
import com.herokuapp.erpmesbackend.erpmesbackend.staff.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public TaskController(TaskRepository taskRepository, EmployeeRepository employeeRepository) {
        this.taskRepository = taskRepository;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/tasks")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskDTO> getAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        List<TaskDTO> taskDTOs = new ArrayList<>();
        tasks.forEach(task -> taskDTOs.add(new TaskDTO(task)));
        return taskDTOs;
    }

    @GetMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.OK)
    public TaskDTO getOneTask(@PathVariable("id") Long id) {
        checkIfTaskExists(id);
        return new TaskDTO(taskRepository.findById(id).get());
    }

    @GetMapping("/kanban/{id}")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskDTO> getTasksByAssignee(@PathVariable("id") Long id) {
        Employee assignee = employeeRepository.findById(id).get();

        checkIfAssigneeExists(assignee.getEmail());
        if (!taskRepository.findTasksByAssigneeIdAndCreationTimeAfterOrderByDeadlineAsc(assignee.getId(),
                LocalDateTime.now().minusDays(28)).isPresent()) {
            return new ArrayList<>();
        }

        List<Task> tasks = taskRepository.findTasksByAssigneeIdAndCreationTimeAfterOrderByDeadlineAsc(assignee.getId(),
                LocalDateTime.now().minusDays(28)).get();
        List<TaskDTO> taskDTOs = new ArrayList<>();
        tasks.forEach(task -> taskDTOs.add(new TaskDTO(task)));
        return taskDTOs;
    }

    @GetMapping("/assignment")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskDTO> getTasksByAssigneeIsNull() {
        if (!taskRepository.findTaskByAssigneeIsNull().isPresent()) {
            return new ArrayList<>();
        }

        List<Task> tasks = taskRepository.findTaskByAssigneeIsNull().get();
        List<TaskDTO> taskDTOs = new ArrayList<>();
        tasks.forEach(task -> taskDTOs.add(new TaskDTO(task)));
        return taskDTOs;
    }

    @PutMapping("/assignment")
    @ResponseStatus(HttpStatus.OK)
    public HttpStatus assignToEmployees(@RequestBody AssignmentRequest assignmentRequest) {
        assignmentRequest.getTaskIds().forEach(this::checkIfTaskExists);
        List<Long> taskIds = new ArrayList<>(assignmentRequest.getTaskIds());
        List<Task> tasks = new ArrayList<>();
        List<Long> assigned = new ArrayList<>();

        assignmentRequest.getAssigneeIds().forEach(this::checkIfAssigneeExists);
        List<Long> assigneeIds = new ArrayList<>(assignmentRequest.getAssigneeIds());
        List<Employee> assignees = new ArrayList<>();

        LocalDateTime startTime = assignmentRequest.getStartTime();
        LocalDateTime counter = assignmentRequest.getStartTime();
        LocalDateTime endTime = assignmentRequest.getStartTime().toLocalDate().atTime(16, 0, 0, 0);

        HashMap<Long, LocalDateTime> schedule = new HashMap<>(); // employee's id and busy time
        assigneeIds.forEach(id -> schedule.put(employeeRepository.findById(id).get().getId(), startTime));

        taskIds.forEach(id -> tasks.add(taskRepository.findById(id).get()));
        assigneeIds.forEach(id -> assignees.add(employeeRepository.findById(id).get()));

        long seed = System.nanoTime();
        Collections.shuffle(assigneeIds, new Random(seed));

        List<Task> sortedTasks = tasks.stream()
                .sorted((t1, t2) -> Task.compareTo(t2, t1))
                .collect(Collectors.toList());

        Iterator<Task> it = sortedTasks.iterator();
        while (counter.isBefore(endTime)) { // przechodź po czasie
            if (schedule.containsValue(counter)) { // jeśli jest określona godzina
                for (Map.Entry<Long, LocalDateTime> entry: schedule.entrySet()) { // przechodź po pracownikach i godzinach dostępu
                    if (entry.getValue().isEqual(counter)) { // jeśli godzina dostępu jest równa określonej godzinie (bo może być nie tylko dla jednego pracownika)
                        for (int i = 0; i < sortedTasks.size(); i++) { // przechodź po zadaniach
                            if (assigned.containsAll(sortedTasks.get(i).getPrecedingTaskIds())) { // jeśli spotkasz zadanie nie posiadające poprzedziających zadań lub zawierające zadania już wykonane
                                sortedTasks.get(i).setAssignee(employeeRepository.findById(1L).get()); // ustaw temu zadaniu pracownika, dla którego go sprawdzasz
                                sortedTasks.get(i).setScheduledTime(counter);
                                assigned.add(sortedTasks.get(i).getId());
                                entry.setValue(counter.plusMinutes(sortedTasks.get(i).getEstimatedTime())); // ustaw w grafiku, aby nowe zadanie mógł wziąć później
                                sortedTasks.remove(i);
                                break;
                            }
                        }
                    }
                }
            }
            counter = counter.plusMinutes(1);
        }

        tasks.forEach(taskRepository::save);
        return HttpStatus.OK;
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDTO addOneTask(@RequestBody TaskRequest taskRequest) {
        String name = taskRequest.getName();

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = ((UserDetails) principal).getUsername();
        Employee author = employeeRepository.findByEmail(username).get();

        Employee assignee = null;
        if (taskRequest.getAssigneeId() != null) {
            checkIfAssigneeExists(taskRequest.getAssigneeId());
            assignee = employeeRepository.findById(taskRequest.getAssigneeId()).get();
        }

        List<Long> precedingTaskIds = null;
        if (taskRequest.getPrecedingTaskIds() != null) {
            precedingTaskIds = new ArrayList<>();
            taskRequest.getPrecedingTaskIds().forEach(this::checkIfTaskExists);
            precedingTaskIds.addAll(taskRequest.getPrecedingTaskIds());
        }

        Integer estimatedTime = taskRequest.getEstimatedTime();
        LocalDateTime deadline = taskRequest.getDeadline();

        LocalDateTime scheduledTime = null;
        if (taskRequest.getScheduledTime() != null) {
            scheduledTime = taskRequest.getScheduledTime();
        }

        String details = null;
        if (taskRequest.getDetails() != null) {
            details = taskRequest.getDetails();
        }

        Type type = null;
        if (taskRequest.getType() != null) {
            type = taskRequest.getType();
        }

        Task task = new Task(name, precedingTaskIds, author, assignee, scheduledTime, estimatedTime, deadline, details, type);

        taskRepository.save(task);
        return new TaskDTO(task);
    }

    @PutMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Task setNextCategory(@PathVariable("id") Long id) {
        checkIfTaskExists(id);
        Task task = taskRepository.findById(id).get();

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = ((UserDetails) principal).getUsername();
        Employee employee = employeeRepository.findByEmail(username).get();

        if (task.getCategory() == Category.TO_DO) {
            task.setCategory(Category.DOING);
            task.setStartTime(LocalDateTime.now());
            task.setStartEmployee(employee);
        } else if (task.getCategory() == Category.DOING) {
            task.setCategory(Category.DONE);
            task.setEndTime(LocalDateTime.now());
            task.setEndEmployee(employee);
        }

        taskRepository.save(task);
        return task;
    }

    @PutMapping("/tasks/{id}/assign")
    public HttpStatus assignToMe(@PathVariable("id") Long id) {
        checkIfTaskExists(id);
        Task task = taskRepository.findById(id).get();

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = ((UserDetails) principal).getUsername();
        Employee employee = employeeRepository.findByEmail(username).get();

        task.setAssignee(employee);
        taskRepository.save(task);

        return HttpStatus.OK;
    }

    private void checkIfTaskExists(Long id) {
        if (!taskRepository.findById(id).isPresent()) {
            throw new NotFoundException("Such task doesn't exist!");
        }
    }

    private void checkIfAssigneeExists(Long id) {
        if (!employeeRepository.findById(id).isPresent()) {
            throw new NotFoundException("Chosen assignee doesn't exist!");
        }
    }

    private void checkIfAssigneeExists(String email) {
        if (!employeeRepository.findByEmail(email).isPresent()) {
            throw new NotFoundException("Chosen assignee doesn't exist!");
        }
    }
}
