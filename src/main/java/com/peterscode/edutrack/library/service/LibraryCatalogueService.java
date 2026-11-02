package com.peterscode.edutrack.library.service;

import com.peterscode.edutrack.library.dto.LibraryRequests;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.entity.Book;
import com.peterscode.edutrack.library.entity.BookCopy;
import com.peterscode.edutrack.library.entity.LibraryCategory;
import com.peterscode.edutrack.library.exception.LibraryRuleException;
import com.peterscode.edutrack.library.repository.BookCopyRepository;
import com.peterscode.edutrack.library.repository.BookRepository;
import com.peterscode.edutrack.library.repository.LibraryCategoryRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LibraryCatalogueService {

    private static final Pattern BOOK_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9\\-_/\\.]{1,50}$");
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("title", "authors", "publicationYear");

    private final LibraryCategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;

    // ==================== Categories ====================

    @Transactional
    @CacheEvict(value = "library-categories", key = "#schoolId")
    public LibraryViews.CategoryView createCategory(Long schoolId, LibraryRequests.CreateCategoryRequest request) {
        if (categoryRepository.existsBySchoolIdAndCode(schoolId, request.code().trim())) {
            throw new LibraryRuleException("DUPLICATE_CATEGORY_CODE", HttpStatus.CONFLICT,
                    "Category code already exists");
        }
        if (categoryRepository.existsBySchoolIdAndName(schoolId, request.name().trim())) {
            throw new LibraryRuleException("DUPLICATE_CATEGORY_NAME", HttpStatus.CONFLICT,
                    "Category name already exists");
        }
        LibraryCategory category = LibraryCategory.builder()
                .schoolId(schoolId)
                .name(request.name().trim())
                .code(request.code().trim().toUpperCase())
                .active(true)
                .build();
        category = categoryRepository.saveAndFlush(category);
        return toCategoryView(category);
    }

    @Transactional
    @CacheEvict(value = "library-categories", key = "#schoolId")
    public LibraryViews.CategoryView updateCategory(Long schoolId, Long id, LibraryRequests.UpdateCategoryRequest request) {
        LibraryCategory category = categoryRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new LibraryRuleException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Category not found"));
        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (!trimmed.equals(category.getName()) && categoryRepository.existsBySchoolIdAndName(schoolId, trimmed)) {
                throw new LibraryRuleException("DUPLICATE_CATEGORY_NAME", HttpStatus.CONFLICT,
                        "Category name already exists");
            }
            category.setName(trimmed);
        }
        if (request.code() != null) {
            String trimmed = request.code().trim().toUpperCase();
            if (!trimmed.equals(category.getCode()) && categoryRepository.existsBySchoolIdAndCode(schoolId, trimmed)) {
                throw new LibraryRuleException("DUPLICATE_CATEGORY_CODE", HttpStatus.CONFLICT,
                        "Category code already exists");
            }
            category.setCode(trimmed);
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return toCategoryView(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "library-categories", key = "#schoolId", unless = "#result == null")
    public Page<LibraryViews.CategoryView> listCategories(Long schoolId, Pageable pageable) {
        return categoryRepository.findBySchoolIdAndActiveTrue(schoolId, pageable)
                .map(this::toCategoryView);
    }

    @Transactional(readOnly = true)
    public LibraryViews.CategoryView getCategory(Long schoolId, Long id) {
        return toCategoryView(categoryRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new LibraryRuleException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Category not found")));
    }

    // ==================== Books ====================

    @Transactional
    public LibraryViews.BookView createBook(Long schoolId, LibraryRequests.CreateBookRequest request) {
        LibraryCategory category = categoryRepository.findBySchoolIdAndId(schoolId, request.categoryId())
                .orElseThrow(() -> new LibraryRuleException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Category not found"));
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new LibraryRuleException("CATEGORY_INACTIVE", HttpStatus.CONFLICT,
                    "Category is inactive");
        }
        Book book = Book.builder()
                .schoolId(schoolId)
                .categoryId(request.categoryId())
                .title(request.title().trim())
                .authors(request.authors() != null ? request.authors().trim() : null)
                .isbn(request.isbn() != null ? request.isbn().trim() : null)
                .publisher(request.publisher() != null ? request.publisher().trim() : null)
                .publicationYear(request.publicationYear())
                .edition(request.edition() != null ? request.edition().trim() : null)
                .language(request.language() != null ? request.language().trim() : null)
                .lendable(request.lendable() != null ? request.lendable() : true)
                .active(true)
                .build();
        book = bookRepository.saveAndFlush(book);
        return toBookView(book, categoryRepository);
    }

    @Transactional
    public LibraryViews.BookView updateBook(Long schoolId, Long id, LibraryRequests.UpdateBookRequest request) {
        Book book = bookRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new LibraryRuleException("BOOK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book not found"));
        if (request.title() != null) book.setTitle(request.title().trim());
        if (request.categoryId() != null) {
            LibraryCategory category = categoryRepository.findBySchoolIdAndId(schoolId, request.categoryId())
                    .orElseThrow(() -> new LibraryRuleException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "Category not found"));
            if (!Boolean.TRUE.equals(category.getActive())) {
                throw new LibraryRuleException("CATEGORY_INACTIVE", HttpStatus.CONFLICT,
                        "Category is inactive");
            }
            book.setCategoryId(request.categoryId());
        }
        if (request.authors() != null) book.setAuthors(request.authors().trim());
        if (request.isbn() != null) book.setIsbn(request.isbn().trim());
        if (request.publisher() != null) book.setPublisher(request.publisher().trim());
        if (request.publicationYear() != null) book.setPublicationYear(request.publicationYear());
        if (request.edition() != null) book.setEdition(request.edition().trim());
        if (request.language() != null) book.setLanguage(request.language().trim());
        if (request.lendable() != null) book.setLendable(request.lendable());
        if (request.active() != null) book.setActive(request.active());
        return toBookView(bookRepository.save(book), categoryRepository);
    }

    @Transactional(readOnly = true)
    public Page<LibraryViews.BookView> listBooks(Long schoolId, Pageable pageable) {
        return bookRepository.findBySchoolIdAndActiveTrue(schoolId, pageable)
                .map(b -> toBookView(b, categoryRepository));
    }

    @Transactional(readOnly = true)
    public LibraryViews.BookView getBook(Long schoolId, Long id) {
        Book book = bookRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new LibraryRuleException("BOOK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book not found"));
        return toBookView(book, categoryRepository);
    }

    // ==================== Search ====================

    @Transactional(readOnly = true)
    public Page<LibraryViews.AvailableBookView> searchAvailableBooks(
            Long schoolId, Long categoryId, String keyword, String isbn, String language,
            String sortBy, String sortDir, int page, int size) {
        if (size > 100) size = 100;
        if (page < 0) page = 0;

        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);

        String escapedKeyword = escapeLike(keyword);
        Page<Book> books = bookRepository.searchAvailableBooks(
                schoolId, categoryId, escapedKeyword, isbn, language, pageable);

        return books.map(b -> {
            long available = copyRepository.countAvailableByBookId(b.getId());
            LibraryViews.CategoryView cat = toCategoryView(
                    categoryRepository.findBySchoolIdAndId(schoolId, b.getCategoryId()).orElse(null));
            return new LibraryViews.AvailableBookView(
                    b.getId(), b.getTitle(), b.getAuthors(), b.getIsbn(), cat,
                    b.getPublicationYear(), available);
        });
    }

    // ==================== Copies ====================

    @Transactional
    public LibraryViews.CopyView createCopy(Long schoolId, LibraryRequests.CreateCopyRequest request) {
        Book book = bookRepository.findBySchoolIdAndId(schoolId, request.bookId())
                .orElseThrow(() -> new LibraryRuleException("BOOK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book not found"));
        String normalized = normalizeBookNumber(request.bookNumber());
        if (copyRepository.existsBySchoolIdAndBookNumber(schoolId, normalized)) {
            throw new LibraryRuleException("DUPLICATE_BOOK_NUMBER", HttpStatus.CONFLICT,
                    "Book number already exists");
        }
        BookCopy copy = BookCopy.builder()
                .schoolId(schoolId)
                .bookId(request.bookId())
                .bookNumber(normalized)
                .shelfLocation(request.shelfLocation() != null ? request.shelfLocation().trim() : null)
                .condition(request.condition() != null ? request.condition().trim() : null)
                .status("AVAILABLE")
                .build();
        copy = copyRepository.saveAndFlush(copy);
        return toCopyView(copy, book);
    }

    @Transactional
    public LibraryViews.BatchCopyResult batchCreateCopies(Long schoolId, LibraryRequests.BatchCreateCopiesRequest request) {
        if (request.bookNumbers().size() > 100) {
            throw new LibraryRuleException("BATCH_TOO_LARGE", HttpStatus.BAD_REQUEST,
                    "Batch size exceeds 100");
        }
        Book book = bookRepository.findBySchoolIdAndId(schoolId, request.bookId())
                .orElseThrow(() -> new LibraryRuleException("BOOK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book not found"));

        List<String> failures = new ArrayList<>();
        int successCount = 0;

        for (String rawNumber : request.bookNumbers()) {
            String normalized = normalizeBookNumber(rawNumber);
            if (copyRepository.existsBySchoolIdAndBookNumber(schoolId, normalized)) {
                failures.add("Duplicate: " + normalized);
                continue;
            }
            BookCopy copy = BookCopy.builder()
                    .schoolId(schoolId)
                    .bookId(request.bookId())
                    .bookNumber(normalized)
                    .status("AVAILABLE")
                    .build();
            copyRepository.save(copy);
            successCount++;
        }
        copyRepository.flush();
        return new LibraryViews.BatchCopyResult((long) successCount, (long) failures.size(), failures);
    }

    @Transactional(readOnly = true)
    public List<LibraryViews.AvailableCopyView> listAvailableCopies(Long schoolId, Long bookId) {
        bookRepository.findBySchoolIdAndId(schoolId, bookId)
                .orElseThrow(() -> new LibraryRuleException("BOOK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book not found"));
        return copyRepository.findAvailableByBookId(bookId, schoolId).stream()
                .map(c -> new LibraryViews.AvailableCopyView(c.getId(), c.getBookNumber(),
                        c.getShelfLocation(), c.getCondition(), c.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LibraryViews.CopyView getCopy(Long schoolId, Long id) {
        BookCopy copy = copyRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new LibraryRuleException("COPY_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Copy not found"));
        Book book = bookRepository.findBySchoolIdAndId(schoolId, copy.getBookId())
                .orElseThrow(() -> new LibraryRuleException("BOOK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book not found"));
        return toCopyView(copy, book);
    }

    // ==================== Helpers ====================

    private String normalizeBookNumber(String raw) {
        String trimmed = raw.trim().toUpperCase();
        if (!BOOK_NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new LibraryRuleException("INVALID_BOOK_NUMBER", HttpStatus.BAD_REQUEST,
                    "Book number contains invalid characters");
        }
        return trimmed;
    }

    private String escapeLike(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Sort buildSort(String sortBy, String sortDir) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "title");
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new LibraryRuleException("INVALID_SORT_FIELD", HttpStatus.BAD_REQUEST,
                    "Sort field not allowed: " + sortBy);
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, sortBy);
    }

    private LibraryViews.CategoryView toCategoryView(LibraryCategory c) {
        if (c == null) return null;
        return new LibraryViews.CategoryView(c.getId(), c.getName(), c.getCode(), c.getActive());
    }

    private LibraryViews.BookView toBookView(Book b, LibraryCategoryRepository repo) {
        LibraryViews.CategoryView cat = toCategoryView(
                repo.findBySchoolIdAndId(b.getSchoolId(), b.getCategoryId()).orElse(null));
        return new LibraryViews.BookView(b.getId(), b.getTitle(), b.getAuthors(), b.getIsbn(),
                b.getPublisher(), b.getPublicationYear(), b.getEdition(), b.getLanguage(),
                b.getLendable(), b.getActive(), cat);
    }

    private LibraryViews.CopyView toCopyView(BookCopy c, Book book) {
        return new LibraryViews.CopyView(c.getId(), c.getBookNumber(), c.getShelfLocation(),
                c.getCondition(), c.getStatus(), c.getAcquiredOn(), c.getLostChargeHint(),
                toBookView(book, categoryRepository));
    }
}
