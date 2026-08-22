package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.dto.CreateDepartmentRequest;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.dto.DepartmentManagementResponse;
import com.isg.backend.modules.user.dto.UpdateDepartmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    private DepartmentService departmentService;

    @BeforeEach
    void setUp() {
        departmentService =
                new DepartmentService(departmentRepository);
    }

    @Test
    void createDepartmentNormalizesCodeAndPersistsActiveDepartment() {
        CreateDepartmentRequest request =
                new CreateDepartmentRequest();

        request.setCode("  kaynak-3  ");
        request.setName("  Yeni Kaynak Bölümü  ");
        request.setDescription("  Yeni üretim alanı  ");

        when(departmentRepository.existsByCode("KAYNAK-3"))
                .thenReturn(false);

        when(departmentRepository.existsByName("Yeni Kaynak Bölümü"))
                .thenReturn(false);

        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> {
                    Department department =
                            invocation.getArgument(0);

                    department.setId(UUID.randomUUID());
                    return department;
                });

        departmentService.createDepartment(request);

        ArgumentCaptor<Department> captor =
                ArgumentCaptor.forClass(Department.class);

        verify(departmentRepository)
                .save(captor.capture());

        Department saved =
                captor.getValue();

        assertThat(saved.getCode())
                .isEqualTo("KAYNAK-3");

        assertThat(saved.getName())
                .isEqualTo("Yeni Kaynak Bölümü");

        assertThat(saved.getDescription())
                .isEqualTo("Yeni üretim alanı");

        assertThat(saved.isActive())
                .isTrue();
    }

    @Test
    void duplicateCodeReturnsConflictAndDoesNotPersist() {
        CreateDepartmentRequest request =
                new CreateDepartmentRequest();

        request.setCode(" kaynak-1 ");
        request.setName("Yeni Bölüm");

        when(departmentRepository.existsByCode("KAYNAK-1"))
                .thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> departmentService.createDepartment(request)
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(departmentRepository, never())
                .save(any(Department.class));
    }

    @Test
    void duplicateNameReturnsConflictAndDoesNotPersist() {
        CreateDepartmentRequest request =
                new CreateDepartmentRequest();

        request.setCode("KAYNAK-9");
        request.setName("Kaynak Bölümü");

        when(departmentRepository.existsByCode("KAYNAK-9"))
                .thenReturn(false);

        when(departmentRepository.existsByName("Kaynak Bölümü"))
                .thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> departmentService.createDepartment(request)
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(departmentRepository, never())
                .save(any(Department.class));
    }

    @Test
    void getAllDepartmentsReturnsMappedDepartments() {
        Department department = Department.builder()
                .id(UUID.randomUUID())
                .code("KAYNAK-1")
                .name("Kaynak Bölümü")
                .description("Ana kaynak alanı")
                .active(true)
                .build();

        when(departmentRepository.findAll())
                .thenReturn(List.of(department));

        List<DepartmentManagementResponse> result =
                departmentService.getAllDepartments();

        assertThat(result)
                .hasSize(1);

        assertThat(result.getFirst().getId())
                .isEqualTo(department.getId());

        assertThat(result.getFirst().getCode())
                .isEqualTo("KAYNAK-1");

        assertThat(result.getFirst().getName())
                .isEqualTo("Kaynak Bölümü");

        assertThat(result.getFirst().getDescription())
                .isEqualTo("Ana kaynak alanı");

        assertThat(result.getFirst().isActive())
                .isTrue();
    }

    @Test
    void updateDepartmentUpdatesMutableFieldsAndKeepsCode() {
        UUID departmentId = UUID.randomUUID();

        Department department = Department.builder()
                .id(departmentId)
                .code("KAYNAK-1")
                .name("Eski Ad")
                .description("Eski açıklama")
                .active(true)
                .build();

        UpdateDepartmentRequest request =
                new UpdateDepartmentRequest();

        request.setName("  Yeni Ad  ");
        request.setDescription("  Yeni açıklama  ");
        request.setActive(false);

        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department));

        when(departmentRepository.existsByName("Yeni Ad"))
                .thenReturn(false);

        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentManagementResponse result =
                departmentService.updateDepartment(
                        departmentId,
                        request
                );

        assertThat(department.getCode())
                .isEqualTo("KAYNAK-1");

        assertThat(department.getName())
                .isEqualTo("Yeni Ad");

        assertThat(department.getDescription())
                .isEqualTo("Yeni açıklama");

        assertThat(department.isActive())
                .isFalse();

        assertThat(result.getCode())
                .isEqualTo("KAYNAK-1");

        assertThat(result.getName())
                .isEqualTo("Yeni Ad");

        assertThat(result.isActive())
                .isFalse();

        verify(departmentRepository)
                .save(department);
    }

    @Test
    void updateDepartmentReturnsNotFoundWhenDepartmentDoesNotExist() {
        UUID departmentId = UUID.randomUUID();

        UpdateDepartmentRequest request =
                new UpdateDepartmentRequest();

        request.setName("Yeni Ad");

        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> departmentService.updateDepartment(
                                departmentId,
                                request
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(departmentRepository, never())
                .save(any(Department.class));
    }

    @Test
    void updateDepartmentReturnsConflictForDuplicateName() {
        UUID departmentId = UUID.randomUUID();

        Department department = Department.builder()
                .id(departmentId)
                .code("KAYNAK-1")
                .name("Eski Ad")
                .active(true)
                .build();

        UpdateDepartmentRequest request =
                new UpdateDepartmentRequest();

        request.setName("Mevcut Bölüm");

        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department));

        when(departmentRepository.existsByName("Mevcut Bölüm"))
                .thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> departmentService.updateDepartment(
                                departmentId,
                                request
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(departmentRepository, never())
                .save(any(Department.class));
    }

    @Test
    void updateDepartmentRejectsBlankName() {
        UUID departmentId = UUID.randomUUID();

        Department department = Department.builder()
                .id(departmentId)
                .code("KAYNAK-1")
                .name("Kaynak Bölümü")
                .active(true)
                .build();

        UpdateDepartmentRequest request =
                new UpdateDepartmentRequest();

        request.setName("   ");

        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> departmentService.updateDepartment(
                                departmentId,
                                request
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(department.getName())
                .isEqualTo("Kaynak Bölümü");

        verify(departmentRepository, never())
                .save(any(Department.class));
    }
}