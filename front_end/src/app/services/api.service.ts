import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Result } from '../common/result';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ApiService {

  private baseUrl = "http://localhost:8080/api/v1"
  constructor(private httpClient: HttpClient) { }

  getResults() {
    let header = new HttpHeaders();
    header.set('Access-Control-Allow-Origin', '*');
    header.set('withcredentials', 'true');

    return this.httpClient.get<any>(this.baseUrl + "/results", {headers: header})
  }

  saveQuery(query) {
    let header = new HttpHeaders();
    header.set('Access-Control-Allow-Origin', '*');
    header.set('withcredentials', 'true');
    return this.httpClient.post(this.baseUrl + "/save-query", {"text": query}, {headers: header})

  }

  getSuggestions(text) {
    let header = new HttpHeaders();
    header.set('Access-Control-Allow-Origin', '*');
    header.set('withcredentials', 'true');

    return this.httpClient.get<any>(this.baseUrl + "/queries/search/suggestions?text="+text+"&size=5"
    , {headers: header})
  }

  addUser() {
    let header = new HttpHeaders();
    header.set('Access-Control-Allow-Origin', '*');
    header.set('withcredentials', 'true');
    return this.httpClient.post(this.baseUrl + "/add-user", {headers: header})
  }

  addHistory(user_id, url_id) {
    let header = new HttpHeaders();
    header.set('Access-Control-Allow-Origin', '*');
    header.set('withcredentials', 'true');
    return this.httpClient.post(this.baseUrl + "/history",{"user": user_id, "url": url_id }, {headers: header})
  }
  
}

