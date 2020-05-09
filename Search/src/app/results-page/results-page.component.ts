import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Observable } from 'rxjs';
import { startWith, map } from 'rxjs/operators';
import { Result } from '../common/result';
import { ResultsService } from '../services/results.service';

@Component({
  selector: 'app-results-page',
  templateUrl: './results-page.component.html',
  styleUrls: ['./results-page.component.scss']
})
export class ResultsPageComponent implements OnInit {
  value = ""
  control = new FormControl();
  streets: string[];
  filteredStreets: Observable<string[]>;
  results: Result[];
  constructor(private resultsService: ResultsService) { 
  }

  ngOnInit() {
    this.streets = null;
    this.filteredStreets = this.control.valueChanges.pipe(
      startWith(''),
      map(value => this._filter(value))
    );

    this.resultsService.getResults().subscribe(data => {
      this.results = null
      if(data){
        console.log(data.page);
        this.results = data._embedded.results;
        console.log(this.results)
      }      
    })
  }

  private _filter(value: string): string[] {
    const filterValue = this._normalizeValue(value);
    return this.streets.filter(street => this._normalizeValue(street).includes(filterValue));
  }

  private _normalizeValue(value: string): string {
    return value.toLowerCase().replace(/\s/g, '');
  }

  Search() {
    if(this.value != ""){
      this.resultsService.saveQuery(this.value).subscribe(data => {
      });
    }
  }
  Suggestions(text : string) {
    this.resultsService.getSuggestions(text).subscribe(data => {
      this.streets = data._embedded.queries;
    })
  }

}
