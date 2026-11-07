package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;


import java.net.URI;

/** Only a repository identifier is used to build the fixed GitHub API URL. */
record GitHubRepository(String owner, String name) {
    static GitHubRepository parse(String value) {
        try {
            var uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getQuery()!=null
                    || uri.getFragment()!=null || uri.getRawPath().contains("%")) throw new IllegalArgumentException();
            String[] parts=uri.getPath().split("/",-1);
            if (parts.length==4 && !parts[3].isEmpty() || parts.length<3 || parts.length>4) throw new IllegalArgumentException();
            String owner=parts[1], repo=parts[2];
            if (repo.endsWith(".git")) repo=repo.substring(0,repo.length()-4);
            if (!owner.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
                    || !repo.matches("[A-Za-z0-9_.-]{1,100}") || repo.equals(".") || repo.equals("..")) throw new IllegalArgumentException();
            return new GitHubRepository(owner,repo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiErrors.invalid("GitHub HTTPS 저장소 URL을 입력하세요.");
        }
    }
    URI readmeApi() { return URI.create("https://api.github.com/repos/"+owner+"/"+name+"/readme"); }
}
