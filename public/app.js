const apiUrl = "/api/students";

const state = {
  students: [],
  status: "All",
};

const els = {
  rows: document.querySelector("#studentRows"),
  empty: document.querySelector("#emptyState"),
  total: document.querySelector("#totalStudents"),
  active: document.querySelector("#activeStudents"),
  gradeCount: document.querySelector("#gradeCount"),
  search: document.querySelector("#searchInput"),
  gradeFilter: document.querySelector("#gradeFilter"),
  sort: document.querySelector("#sortSelect"),
  add: document.querySelector("#addStudentButton"),
  dialog: document.querySelector("#studentDialog"),
  form: document.querySelector("#studentForm"),
  formTitle: document.querySelector("#formTitle"),
  close: document.querySelector("#closeDialogButton"),
  cancel: document.querySelector("#cancelButton"),
  deleteButton: document.querySelector("#deleteButton"),
  id: document.querySelector("#studentId"),
  name: document.querySelector("#name"),
  rollNo: document.querySelector("#rollNo"),
  grade: document.querySelector("#grade"),
  email: document.querySelector("#email"),
  phone: document.querySelector("#phone"),
  score: document.querySelector("#score"),
  statusInput: document.querySelector("#status"),
  notes: document.querySelector("#notes"),
};

document.querySelectorAll(".side-nav button").forEach((button) => {
  button.addEventListener("click", () => {
    state.status = button.dataset.status;
    document.querySelectorAll(".side-nav button").forEach((item) => item.classList.toggle("active", item === button));
    render();
  });
});

els.search.addEventListener("input", render);
els.gradeFilter.addEventListener("change", render);
els.sort.addEventListener("change", render);
els.add.addEventListener("click", () => openDialog());
els.close.addEventListener("click", closeDialog);
els.cancel.addEventListener("click", closeDialog);

els.form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const student = getFormData();
  const id = els.id.value;
  const response = await fetch(id ? `${apiUrl}/${id}` : apiUrl, {
    method: id ? "PUT" : "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(student),
  });

  if (!response.ok) {
    const error = await response.json();
    alert(error.error || "Could not save student");
    return;
  }

  closeDialog();
  await loadStudents();
});

els.deleteButton.addEventListener("click", async () => {
  const id = els.id.value;
  if (!id) return;
  const response = await fetch(`${apiUrl}/${id}`, { method: "DELETE" });
  if (!response.ok) {
    alert("Could not delete student");
    return;
  }
  closeDialog();
  await loadStudents();
});

async function loadStudents() {
  const response = await fetch(apiUrl);
  state.students = await response.json();
  updateGradeFilter();
  render();
}

function render() {
  const students = filteredStudents();
  els.rows.innerHTML = "";
  els.empty.hidden = students.length > 0;

  students.forEach((student) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>
        <div class="student-name">
          <strong>${escapeHtml(student.name)}</strong>
          <span>${escapeHtml(student.notes || "No notes")}</span>
        </div>
      </td>
      <td>${escapeHtml(student.rollNo)}</td>
      <td>${escapeHtml(student.grade)}</td>
      <td>
        <div>${escapeHtml(student.email || "No email")}</div>
        <div class="muted">${escapeHtml(student.phone || "No phone")}</div>
      </td>
      <td>${escapeHtml(student.score || "-")}</td>
      <td><span class="status-pill status-${student.status.toLowerCase()}">${escapeHtml(student.status)}</span></td>
      <td>
        <div class="row-actions">
          <button class="mini-button" data-action="edit" type="button">Edit</button>
          <button class="mini-button" data-action="toggle" type="button">${student.status === "Active" ? "Deactivate" : "Activate"}</button>
        </div>
      </td>
    `;
    row.querySelector('[data-action="edit"]').addEventListener("click", () => openDialog(student));
    row.querySelector('[data-action="toggle"]').addEventListener("click", () => toggleStatus(student));
    els.rows.append(row);
  });

  els.total.textContent = state.students.length;
  els.active.textContent = state.students.filter((student) => student.status === "Active").length;
  els.gradeCount.textContent = new Set(state.students.map((student) => student.grade)).size;
}

function filteredStudents() {
  const query = els.search.value.trim().toLowerCase();
  const grade = els.gradeFilter.value;
  const sort = els.sort.value;

  return state.students
    .filter((student) => {
      const searchable = [student.name, student.rollNo, student.grade, student.email, student.phone, student.status, student.notes].join(" ").toLowerCase();
      const matchesSearch = !query || searchable.includes(query);
      const matchesStatus = state.status === "All" || student.status === state.status;
      const matchesGrade = grade === "All" || student.grade === grade;
      return matchesSearch && matchesStatus && matchesGrade;
    })
    .sort((a, b) => String(a[sort] || "").localeCompare(String(b[sort] || ""), undefined, { numeric: true }));
}

function updateGradeFilter() {
  const current = els.gradeFilter.value;
  const grades = [...new Set(state.students.map((student) => student.grade))].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  els.gradeFilter.innerHTML = `<option value="All">All grades</option>${grades.map((grade) => `<option value="${escapeHtml(grade)}">${escapeHtml(grade)}</option>`).join("")}`;
  els.gradeFilter.value = grades.includes(current) ? current : "All";
}

function openDialog(student) {
  const editing = Boolean(student);
  els.formTitle.textContent = editing ? "Edit Student" : "Add Student";
  els.deleteButton.hidden = !editing;
  els.id.value = student?.id || "";
  els.name.value = student?.name || "";
  els.rollNo.value = student?.rollNo || "";
  els.grade.value = student?.grade || "";
  els.email.value = student?.email || "";
  els.phone.value = student?.phone || "";
  els.score.value = student?.score || "";
  els.statusInput.value = student?.status || "Active";
  els.notes.value = student?.notes || "";
  els.dialog.showModal();
  els.name.focus();
}

function closeDialog() {
  els.form.reset();
  els.dialog.close();
}

async function toggleStatus(student) {
  const next = { ...student, status: student.status === "Active" ? "Inactive" : "Active" };
  const response = await fetch(`${apiUrl}/${student.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(next),
  });
  if (response.ok) {
    await loadStudents();
  }
}

function getFormData() {
  return {
    name: els.name.value.trim(),
    rollNo: els.rollNo.value.trim(),
    grade: els.grade.value.trim(),
    email: els.email.value.trim(),
    phone: els.phone.value.trim(),
    score: els.score.value.trim(),
    status: els.statusInput.value,
    notes: els.notes.value.trim(),
  };
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

loadStudents();
