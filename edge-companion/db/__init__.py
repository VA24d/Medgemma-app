from db.repository import Repository

repo: Repository | None = None


def get_repo() -> Repository:
    global repo
    if repo is None:
        repo = Repository()
    return repo
