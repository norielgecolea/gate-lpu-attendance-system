package org.nors.dev.codes.lpu.service;

import java.util.HashSet;
import java.util.Set;
import org.nors.dev.codes.lpu.dto.PhotoCleanupResponse;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoCleanupService {

    private final PhotoStorageService photoStorageService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public PhotoCleanupService(
            PhotoStorageService photoStorageService,
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository
    ) {
        this.photoStorageService = photoStorageService;
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public PhotoCleanupResponse preview() {
        return photoStorageService.inspectUnused(referencedPhotoPaths());
    }

    @Transactional(readOnly = true)
    public PhotoCleanupResponse deleteUnused() {
        return photoStorageService.deleteUnused(referencedPhotoPaths());
    }

    private Set<String> referencedPhotoPaths() {
        Set<String> paths = new HashSet<>(studentRepository.findAllPhotoPaths());
        paths.addAll(employeeRepository.findAllPhotoPaths());
        return paths;
    }
}
